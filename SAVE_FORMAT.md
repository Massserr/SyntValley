# SyntValley — формат сохранения

Статус: proposed persistent contract  
Начальная schema: `1`  
Authoritative storage: Overworld-scoped NeoForge/Minecraft `SavedData` (`syntvalley.dat`)

## 1. Цели

- Сохранять стабильную идентичность деревень, жителей, задач, проектов, памяти и решений.
- Не писать собственные JSON-файлы на диск каждый tick.
- Использовать штатный world save и dirty semantics Minecraft.
- Отделить persistent domain state от runtime entities, paths, futures, queues и caches.
- Поддерживать последовательные миграции без молчаливой потери неизвестных данных.
- Восстанавливаться после unload/restart без дублирования entity или двойного расхода ресурсов.
- Иметь bounded collections и явную политику очистки.
- Разрешить человекочитаемый export для диагностики, не превращая его во второй источник истины.

## 2. Storage topology

### Canonical world state

Один `SyntValleySavedData` прикрепляется к `MinecraftServer#overworld` / Overworld data storage. Он содержит межпространственный индекс: у location всегда записан dimension key, поэтому Village/Citizen может находиться не только в Overworld.

Файл создаётся штатным `SavedData` под именем `syntvalley`. Точное физическое имя/расположение определяется Minecraft 1.21.1 data storage; код не должен вручную открывать или перезаписывать canonical `.dat`.

### Local bindings

- `SyntCoreBlockEntity` сохраняет только `VillageId`, binding generation/revision и минимальные локальные данные блока.
- `VillageConsoleBlockEntity` сохраняет `VillageId`/binding revision.
- `SyntCitizenEntity` сохраняет `CitizenId`, `VillageId`, binding generation и минимальные entity-local данные, необходимые для безопасного rebind.
- Полная личность, needs, profession, memory и task state не дублируются в block/entity NBT.

### Human-readable data

JSON/data-pack ресурсы удобны и нормативны для:

- profession definitions;
- building template metadata;
- task/project definitions;
- localization-adjacent prompt fragments без пользовательских secrets;
- test fixtures/golden protocol examples.

Admin/debug export может создавать snapshot JSON по явной команде в отдельной export-папке мира, с redaction и atomic temp→replace. Export не загружается автоматически и не является backup/authoritative state.

## 3. Root NBT shape

Ниже используется JSON-подобная запись для читаемости; физически это typed NBT. Имена полей `snake_case`, стабильны внутри schema version.

```json
{
  "schema_version": 1,
  "world_instance_id": "UUID",
  "data_revision": 184,
  "created_at_epoch_ms": 1783620000000,
  "last_flush_game_time": 123456,
  "villages": [],
  "citizens": [],
  "tasks": [],
  "projects": [],
  "memories": [],
  "decisions": []
}
```

### Root fields

| Поле | NBT type | Обяз. | Смысл |
|---|---|---:|---|
| `schema_version` | Int | да | Версия persistent schema, не protocol/network version. |
| `world_instance_id` | IntArray UUID или canonical UUID representation | да | Уникальный id этого SyntValley world state; защищает от случайной склейки export/async result. |
| `data_revision` | Long | да | Монотонная общая revision commit’ов; diagnostic/order marker. |
| `created_at_epoch_ms` | Long | да | Wall-clock metadata; не используется для gameplay. |
| `last_flush_game_time` | Long | да | Последнее время обслуживания/coalescing dirty queue; не обещает отдельную физическую запись. |
| Collections | List<Compound> | да | Нормализованные записи с собственными typed IDs. |

UUID может физически кодироваться эффективным стандартным способом Minecraft; schema tests фиксируют round-trip и не позволяют разным codec’ам расходиться.

Collections хранятся списками records, а не dynamic compound keys. Loader строит runtime maps и отклоняет duplicate IDs.

## 4. Village record

```json
{
  "id": "UUID",
  "revision": 31,
  "name": "Берёзовая долина",
  "lifecycle": "ACTIVE",
  "core": {
    "dimension": "minecraft:overworld",
    "pos": 274877911104,
    "binding_generation": 1
  },
  "created_game_time": 1000,
  "last_simulated_game_time": 123450,
  "resident_ids": ["UUID"],
  "task_ids": ["UUID"],
  "project_ids": ["UUID"],
  "memory_ids": ["UUID"],
  "priorities": {
    "food": 50,
    "safety": 50,
    "materials": 50,
    "housing": 50,
    "tools": 50,
    "social": 50
  },
  "policy": {
    "auto_accept_safe_projects": false,
    "max_active_projects": 2
  },
  "known_zones": [],
  "alerts": []
}
```

### Rules

- `name` ограничено по code points и форматированию; gameplay не использует его как ID.
- Lifecycle v1: `ACTIVE`, `ORPHANED`, `SUSPENDED`, `ARCHIVED`.
- `core` отсутствует только для `ORPHANED`/migration recovery; active village имеет один binding.
- `pos` — packed `BlockPos#asLong` семантика версии 1.21.1; dimension хранится отдельно.
- Priority values — bounded integers `0..100`; crisis override transient и пересчитывается, если только не имеет отдельной persistent policy.
- Списки ссылок дедуплицированы и bounded. Loader проверяет обратные связи, но не доверяет им больше, чем owner fields в records.
- Alerts сохраняются только если это долговременные user-visible события. Derived shortage alerts пересчитываются.

## 5. Citizen record

```json
{
  "id": "UUID",
  "revision": 22,
  "village_id": "UUID",
  "lifecycle": "ACTIVE",
  "profile": {
    "display_name": "Мира",
    "created_game_time": 1800,
    "skin_variant": "syntvalley:default_03"
  },
  "entity_binding": {
    "entity_uuid": "UUID",
    "binding_generation": 2,
    "last_known_location": {
      "dimension": "minecraft:overworld",
      "pos": 274877911104
    },
    "presence": "LOADED"
  },
  "profession": {
    "definition_id": "syntvalley:farmer",
    "level": 1,
    "experience": 240,
    "changed_game_time": 4200
  },
  "needs": {
    "last_updated_game_time": 123420,
    "hunger": 340,
    "rest": 760,
    "safety": 900,
    "tools": 500,
    "social": 620,
    "purpose": 710
  },
  "personality": {
    "sociability": 35,
    "diligence": 82,
    "caution": 67,
    "curiosity": 48,
    "cooperativeness": 74
  },
  "mood": {
    "baseline": 40,
    "last_event_game_time": 122000
  },
  "habit_ids": ["syntvalley:early_worker"],
  "goal_ids": ["improve_food_reserve"],
  "active_task_id": "UUID",
  "memory_ids": ["UUID"],
  "home": {"dimension": "minecraft:overworld", "pos": 274877911104},
  "workplace": {"dimension": "minecraft:overworld", "pos": 274877911105}
}
```

### Rules

- Lifecycle v1: `ACTIVE`, `MISSING_ENTITY`, `DECEASED`, `RETIRED`, `ARCHIVED`.
- `entity_uuid` — last canonical bound Minecraft entity UUID, не runtime integer id.
- `presence` сохраняется только как hint/audit; после load всегда пересчитывается. `LOADED` из save не считается истинным.
- Need values используют fixed integer scale `0..1000`, чтобы избежать platform-dependent float drift и неясных процентов.
- Personality values `0..100`; defaults/migration детерминированы от stable CitizenId/seed при отсутствии старого поля.
- Derived mood modifiers, current navigation, animation, sensor results и path не сохраняются.
- Profession definition может отсутствовать после datapack change; тогда lifecycle профессии runtime получает `MISSING_DEFINITION`, Citizen не удаляется.
- `active_task_id` может отсутствовать. После load задача и binding проходят reconciliation до исполнения.
- `home`/`workplace` — soft references; block existence перепроверяется при использовании.

## 6. Memory record

```json
{
  "id": "UUID",
  "owner": {"kind": "CITIZEN", "id": "UUID"},
  "kind": "EVENT",
  "summary": "Деревня завершила первый склад.",
  "created_game_time": 120000,
  "last_recalled_game_time": 121000,
  "salience": 75,
  "confidence": 100,
  "source": "OBSERVED_EVENT",
  "source_event_key": "syntvalley:project_completed/UUID",
  "participant_citizen_ids": ["UUID"],
  "related_village_id": "UUID",
  "pinned": false
}
```

### Rules

- Owner kind: `CITIZEN` или `VILLAGE`.
- Kind: `EVENT`, `RELATIONSHIP`, `ACHIEVEMENT`, `CONCERN`, `SUMMARY`.
- Source: `OBSERVED_EVENT`, `PLAYER_SAID`, `LLM_SUGGESTED`, `SYSTEM_SUMMARY`, `MIGRATED`.
- `summary` bounded; full chat/prompt/raw LLM response не сохраняется.
- `source_event_key` позволяет дедупликацию, но не содержит произвольный длинный текст.
- `confidence` не превращает player/LLM statement в факт; source semantics всегда сохраняется.
- Retention caps применяются per citizen/village и глобально. Pinned reserved slots тоже ограничены.
- Удаление memory record атомарно удаляет refs из owners либо refs пересобираются индексом при materialization.

## 7. Task record

```json
{
  "id": "UUID",
  "revision": 9,
  "village_id": "UUID",
  "owner_citizen_id": "UUID",
  "parent_project_id": "UUID",
  "definition_id": "syntvalley:deliver_resource",
  "state": "PAUSED",
  "priority": 70,
  "created_game_time": 110000,
  "updated_game_time": 123400,
  "attempt_count": 2,
  "last_outcome": "TARGET_UNLOADED",
  "inputs": {
    "resource_id": "minecraft:oak_planks",
    "amount": 16,
    "target_ref": "project_staging:UUID"
  },
  "progress": {
    "stage": "DELIVERY",
    "completed_units": 0,
    "required_units": 16
  },
  "not_before_game_time": 124000
}
```

### Persistent task state

Сохраняются semantic inputs, state, bounded progress, attempt/backoff counters и terminal outcome. Не сохраняются:

- navigation/path nodes;
- entity handles;
- open inventory handles;
- Java lambdas/goal objects;
- locks, futures и thread state;
- transient task lease;
- предположение, что предмет всё ещё физически зарезервирован.

### Restart reconciliation

- `RUNNING` и `RESERVED` загружаются как `PAUSED_RECONCILE`, затем проверяются owner, resources и world state.
- `READY` остаётся candidate, но очередь строится заново.
- Terminal states остаются для bounded audit/parent completion, затем очищаются retention policy.
- Missing definition переводит task в `BLOCKED_MISSING_DEFINITION`, а не исполняет неизвестный input.
- Retry count никогда не сбрасывается бесконечно при restart.

## 8. Project/building record

```json
{
  "id": "UUID",
  "revision": 13,
  "village_id": "UUID",
  "proposer": {"kind": "CITIZEN", "id": "UUID"},
  "type": "BUILDING",
  "template_id": "syntvalley:small_storehouse",
  "template_version": 1,
  "purpose": "FOOD_STORAGE",
  "state": "BUILDING",
  "created_game_time": 100000,
  "updated_game_time": 123300,
  "placement": {
    "dimension": "minecraft:overworld",
    "anchor_pos": 274877911104,
    "rotation": "CLOCKWISE_90",
    "plan_hash": "bounded-hash"
  },
  "bill_of_materials": [
    {"resource_id": "minecraft:oak_planks", "required": 64, "delivered": 24}
  ],
  "task_ids": ["UUID"],
  "completed_stage": 1,
  "failure": null
}
```

### Rules

- State: `PROPOSED`, `VALIDATING`, `AWAITING_RESOURCES`, `READY`, `BUILDING`, `PAUSED`, `BLOCKED`, `COMPLETED`, `CANCELLED`, `FAILED`, `MISSING_DEFINITION`.
- Placement появляется только после Java planner/validator. До этого template proposal не содержит block coordinates.
- `plan_hash` помогает обнаружить несовместимую замену template definition; не является security proof.
- Block-by-block cursor сохраняется только на уровне безопасной stage/progress позиции. Перед продолжением каждый block step сравнивает current world state и не перезаписывает неожиданный чужой блок.
- Bill of materials — план/учёт delivery, не доказательство наличия предметов; inventory truth перепроверяется.
- При template version mismatch применяется explicit template migration/revalidation либо project блокируется.

## 9. Decision record

```json
{
  "id": "UUID",
  "request_id": "UUID",
  "subject": {"kind": "VILLAGE", "id": "UUID"},
  "decision_kind": "VILLAGE_PLANNING",
  "protocol_version": "1.0",
  "backend": "ollama",
  "model": "qwen3:8b",
  "snapshot_revision": 42,
  "created_game_time": 123000,
  "completed_game_time": 123120,
  "proposal_type": "propose_project",
  "outcome": "REJECTED",
  "reason_code": "STALE_SNAPSHOT",
  "rationale": "Current food storage is nearly full.",
  "latency_ms": 5300
}
```

### Rules

- Decision log bounded per subject/world by count и age.
- `id` server-derived/idempotent; повторный response не создаёт новую запись/задачу.
- Thinking/chain-of-thought, system prompt, raw HTTP body и secrets не сохраняются.
- Rationale/text хранится с cap и может быть отключено/redacted config’ом; reason code сохраняется всегда.
- Pending/in-flight HTTP requests не сохраняются. После restart scheduler решает заново по актуальному state.
- Backend metrics counters могут агрегироваться, а не создавать запись на каждый незначимый request.

## 10. Resource accounting и reservations

`ResourceLedger` можно частично сохранять только как cache metadata, но physical ItemStacks остаются истиной. В schema 1 рекомендуемый подход:

- persistent project requirements/delivered counts;
- persistent task intent;
- transient inventory index;
- transient resource reservations/leases;
- rebuild/reconciliation после load и при inventory invalidation.

Если позднее reservation станет persistent, она обязана иметь expiry, owner task/project, конкретный source fingerprint и reconciliation state. Нельзя после restart автоматически считать предметы зарезервированными и списывать их без проверки.

## 11. Persistent vs transient matrix

| Данные | Persistent | Причина/восстановление |
|---|---:|---|
| Stable typed IDs | да | Основа ссылочной целостности |
| Village/citizen revisions | да | Optimistic concurrency и sync |
| Needs/personality/base mood | да | Продолжение характера/состояния |
| Derived mood score | нет | Пересчитывается |
| Profession/skills | да | Долговременная прогрессия |
| Task semantic state/progress | да | Продолжение работы после reconcile |
| Path/navigation/goal instance | нет | Строится в loaded world |
| Project placement plan/stages | да | Безопасное продолжение стройки |
| Inventory scan cache | нет | Пересканируется |
| Resource lease/reservation | нет в v1 | Перепроверяется/создаётся заново |
| Memories/decision outcomes | да, bounded | Игровая память и аудит |
| Raw LLM response/thinking | нет | Privacy/size/security |
| Pending LLM future/queue | нет | Scheduler создаёт новое решение при нужде |
| LLM circuit state | нет | Runtime operational state |
| Client subscriptions/UI cache | нет | Создаются при открытии screen |
| Tick cursors/budget debt | нет | Инициализируются безопасно |
| Core/entity bindings | да + local hint | Reconciliation с world objects |

## 12. Dirty tracking и deferred save bookkeeping

### Модель

Mutation проходит только через server-thread repository/unit-of-work:

1. Aggregate загружается по typed ID.
2. Command проверяет expected revision/invariants.
3. Успешный commit увеличивает aggregate/global revision и атомарно заменяет immutable persistent record в актуальном in-memory root `SyntValleySavedData`.
4. `DirtyTracker` добавляет deduplicated `DirtyKey(kind, id)`, объединяет reason mask и гарантирует `SyntValleySavedData#setDirty()` при commit.
5. Runtime/read model сразу видит изменение; `setDirty()` лишь ставит marker и не выполняет disk I/O.
6. `PersistenceCoordinator` периодически забирает bounded batch dirty keys для coalescing derived indexes, sync hints, metrics и retention work. Новое canonical состояние не существует только в этой очереди.

Dirty queue bounded не путём отбрасывания сохранения. При достижении soft limit она коалесцирует keys и повышает приоритет flush; hard limit переводит runtime в защитный режим, где новые non-critical mutations отклоняются либо bookkeeping синхронно обслуживается по безопасной policy. Поскольку current record и dirty marker обновлены при commit, переполнение очереди не должно откатывать canonical state. Потеря dirty reason/sync work молча всё равно запрещена.

### Flush points

| Trigger | Поведение |
|---|---|
| Periodic cadence | Bounded drain/coalescing derived work; current root и dirty marker уже обновлены commit’ом. |
| Minecraft world save hook | Полный drain bookkeeping на server thread с instrumentation; проверить current root/marker; затем штатный `SavedData` save. |
| Server stopping | Запрет новых jobs/commands, drain LLM completions only if safe, полный bookkeeping flush, shutdown executor; штатное сохранение current root. |
| Critical lifecycle event | Можно повысить priority dirty key; не выполнять ручную запись файла. |
| Debug export | Immutable snapshot current root; отдельный bounded I/O task, не canonical save. |

`setDirty()` может вызываться при каждом aggregate commit или только при переходе clean→dirty чаще, чем физический disk write: это штатный контракт `SavedData`, а не запись файла. Мод не вызывает полный `saveEverything` ради каждой деревни.

## 13. Load pipeline

1. Получить raw root NBT через `SavedData` loader.
2. Проверить наличие/type/диапазон `schema_version` до остальных полей.
3. Если version ниже current — создать defensive copy и применить непрерывную migration chain.
4. Decode в persistent records с field/list/string caps.
5. Проверить duplicate IDs, required refs, enum/number bounds и collection totals.
6. Построить runtime indexes; неизвестные optional definitions получают recoverable state.
7. Выполнить cross-record reconciliation без доступа к незагруженным chunks.
8. При появлении entities/cores их lifecycle events завершают binding reconciliation.
9. Mark dirty только если migration/repair действительно изменила canonical representation.
10. Не возобновлять pending LLM requests; normal scheduler начинает после startup grace period.

## 14. Binding reconciliation

### Citizen

- Entity без valid `CitizenId` не присваивается случайному record; используется controlled recovery/despawn/quarantine policy.
- Entity с ID отсутствующего record не создаёт полноценного Citizen молча. Dev/repair command может восстановить с явной пометкой.
- Две loaded entities с одним `CitizenId`: canonical `entity_uuid` + highest binding generation определяют candidate; конфликт переводится в quarantine, никаких двух workers.
- Record без entity: `MISSING_ENTITY`; auto-respawn только если отдельная gameplay policy это разрешает и доказано, что entity не просто unloaded.
- Death event на server thread переводит canonical record в `DECEASED`, отменяет/переназначает tasks и создаёт observed memory.

### Core/Console

- Core binding должен совпадать по VillageId, dimension/pos и generation.
- Несколько cores на одну active village не выбираются по принципу «последний победил»; это conflict с repair flow.
- Missing/removed core переводит village в `ORPHANED`, но не удаляет records.
- Console является secondary binding; потеря Console влияет на UI access, не на существование Village.

## 15. Schema versioning

### Правила

- `schema_version` — monotonically increasing integer.
- Каждый шаг `N -> N+1` — отдельный deterministic migrator с fixture tests.
- Запрещён migrator `1 -> current`, который знает все исторические формы.
- Migration не обращается к live entities, chunks, registries с нестабильным availability или LLM.
- Resource identifiers остаются namespaced strings; remap table явный и тестируемый.
- Добавление optional derived field может не требовать version bump; изменение semantics/type/requiredness требует.
- После migration выполняется полный validation before commit.

### Future version

Если `schema_version > supported`:

- fail closed;
- canonical tag не перезаписывается и не «понижается»;
- SyntValley runtime для мира не начинает gameplay mutation;
- оператор получает ясное сообщение с found/supported versions и инструкцией использовать совместимую версию мода;
- нельзя загрузить пустое состояние и затем затереть более новый save.

### Migration failure/corruption

- Ошибка имеет path/category без вывода секретов или гигантского tag.
- Runtime не продолжает с частично мигрированным object graph.
- Перед destructive migration реализация должна использовать безопасную backup/recovery стратегию, согласованную со штатными world backups; конкретный механизм фиксируется в slice реализации и проверяется на Windows/Linux.
- Automatic «drop bad records and continue» запрещён для Village/Citizen identity. Отдельный opt-in repair tool может экспортировать отчёт и потребовать подтверждение.

## 16. Bounds и retention

Hard caps защищают loader от повреждённого/злонамеренного save. Точные значения калибруются до релиза, но schema обязана иметь cap для:

- villages per world;
- citizens/tasks/projects/memories/decisions total и per village;
- refs per record;
- string length и compound/list depth;
- task/project input fields;
- custom/unknown tags (обычно запрещены);
- decision log age/count;
- terminal task retention;
- pinned memories.

При достижении gameplay cap новые объекты отклоняются с видимой причиной. Loader hard-cap violation считается corruption/unsupported data, а не поводом незаметно обрезать identity records.

## 17. Save/load tests

### Codec

- Empty schema-1 root round-trip.
- Полный village/citizen/task/project/memory/decision round-trip.
- Unicode names/text, UUIDs, negative coordinates, multiple dimensions.
- Optional field defaults и enum bounds.
- Duplicate IDs/refs, invalid type, excess length/count, unknown required semantics.
- Golden NBT fixtures, стабильные между refactors.

### Migration

- Fixture для каждой исторической schema.
- Каждый шаг отдельно и вся chain до current.
- Idempotence current decode/encode; migrator не запускается повторно.
- Missing definitions/renamed ids через explicit mapping.
- Future version fail closed и source remains unmodified.
- Corrupt migration не создаёт частичный runtime.

### Lifecycle

- Save/restart сохраняет IDs и revisions.
- `RUNNING` task становится reconcile state и не повторяет already-completed transfer/block step.
- In-flight LLM request не возобновляется и stale completion от старого runtime игнорируется.
- Entity duplicate/missing/core orphan conflict flows.
- Dirty coalescing: множество mutations одного aggregate создают один key, но сохраняют latest state.
- Pre-save/server-stop drain не теряет pending keys.
- Ollama выключен: load/save полностью работоспособны.

## 18. Primary reference

- [NeoForge 1.21.1 — Saved Data](https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/)
- [NeoForge 1.21.1 — Data Attachments](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/)
- [NeoForge 1.21.1 — Block Entities](https://docs.neoforged.net/docs/1.21.1/blockentities/)

Официальный контракт `SavedData` требует `setDirty()` после изменения, иначе `save` не будет вызван. Поэтому current root record и dirty marker обновляются при успешном commit. Deferred queue коалесцирует последующее bookkeeping/derived work и никогда не является единственным местом, где живёт ещё не сохранённое canonical изменение.
