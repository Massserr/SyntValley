# SyntValley — риски и архитектурные решения

Статус: живой реестр  
Правило: изменение принятого решения сначала фиксируется здесь и в затронутом contract document, затем реализуется

## 1. Шкалы

- Вероятность: Low / Medium / High.
- Влияние: Low / Medium / High / Critical.
- Статус решения: `ACCEPTED`, `PROPOSED`, `SUPERSEDED`, `REJECTED`, `EXPERIMENTAL`.
- «Пересмотреть» означает условие, при котором решение можно открыть заново; это не обещание автоматически его сменить.

## 2. Реестр рисков

| ID | Риск | Вероятность | Влияние | Ранний сигнал | Мера/граница |
|---|---|---:|---:|---|---|
| R-01 | LLM блокирует server thread | Medium | Critical | Tick spike совпадает с HTTP/parse | Только bounded async backend; immutable snapshots; completion inbox; thread assertions |
| R-02 | Неограниченная queue/thread growth | Medium | Critical | Queue/RAM/threads растут при недоступном Ollama | Fixed executor, bounded queue, rejection, per-village/citizen in-flight cap, circuit breaker |
| R-03 | Невалидное/опасное LLM action | High | Critical | Unknown action, forged id, direct block command | Strict schema, capabilities, whitelist validator, no generic dispatcher, Java re-check |
| R-04 | Prompt injection через player/memory | High | High | Модель повторяет commands/игнорирует schema | Untrusted quoted data, fact IDs, structured output, content/action validation |
| R-05 | Qwen structured output нестабилен | Medium | High | Parse/schema rejection rate | Low temperature, JSON schema, think disabled, one optional repair, fallback, metrics |
| R-06 | LLM недоступен/медленен | High | High | Timeout/circuit opens | Deterministic planner/dialogue fallback; no gameplay dependency on availability |
| R-07 | Prompt/memory бесконтрольно растут | High | High | Latency/token/RAM trend | Bounded records, salience, dedupe, pagination, character/token budgets |
| R-08 | Pathfinding многих жителей снижает TPS | High | High | Path request/time spikes | Staggering, path budget, active task cap, backoff, loaded-chunk constraint |
| R-09 | Все системы обходят всех жителей каждый tick | Medium | Critical | Simulation time ~ citizens × systems | Central cadence scheduler, round-robin cursors, tick budgets, event-driven updates |
| R-10 | Save corruption/небезопасная migration | Medium | Critical | Decode/migration errors, missing IDs | Versioned root, golden fixtures, sequential migrations, fail closed, recovery/export |
| R-11 | Domain/entity binding расходится | Medium | Critical | Duplicate citizen, missing worker, repeated task | Stable IDs, generation, reconciliation, quarantine conflicts, lifecycle states |
| R-12 | Dirty queue теряет изменения | Low | Critical | State откатывается после save/restart | Dedup without drop, pre-save full drain, hard-limit protection, dirty metrics/tests |
| R-13 | Двойной расход/строительство после restart | Medium | High | Повтор transfer/block step | Persist semantic progress, transient leases, postcondition checks, reconciliation |
| R-14 | Template build разрушает чужие блоки | Medium | Critical | Unexpected replacement/protection reports | Placement validation, protected-area adapters, stage preconditions, stop on conflict |
| R-15 | Resource ledger устаревает | High | Medium | Task считает предметы, которых нет | Ledger as cache, reservations, execution-time inventory truth, invalidation/replan |
| R-16 | Client подделывает admin/chat requests | High | Critical | Requests без context/creative | Server-derived sender, sessions, permission/distance/revision/rate checks |
| R-17 | UI flood/слишком большие payloads | Medium | High | Network bytes/allocations grow | DTO caps, pagination, coalesced deltas, screen subscriptions, per-player budget |
| R-18 | Client classes загружаются на dedicated server | Medium | High | `NoClassDefFoundError` | Physical side entry point, dependency rules, dedicated smoke test every UI slice |
| R-19 | Offline simulation конфликтует с реальным миром | Medium | High | Hidden production/block divergence | В ранних версиях loaded chunks only; future coarse mode cannot mutate blocks/inventories |
| R-20 | Personality делает поведение хаотичным | Medium | Medium | Safety/tasks проигрывают случайности | Traits modify bounded soft scores only; hard policies dominate; deterministic tests |
| R-21 | Жители выглядят как scripts, несмотря на LLM | Medium | High | Повторные одинаковые loops/реплики | Event-driven goals, memory/traits, rare planning, observed consequences, не только chat |
| R-22 | Scope explosion | High | High | Семьи/рейды/voice/gen-build до core loop | Явные non-goals и slices; новая система требует contract/acceptance/budget review |
| R-23 | Сломанная совместимость NeoForge 1.21.1 | Medium | High | API names из newer docs не компилируются | Pin MDK versions; сверять 1.21.1 docs/source; build/Game Tests per slice |
| R-24 | Копирование старого проекта переносит ошибки/IP | Medium | High | Совпадающие layout/classes/bugs | Old project only for ideas/anti-patterns; clean design and implementation |
| R-25 | Logs раскрывают chat/prompt/secrets | Medium | High | Raw HTTP/auth/player text in logs | Redaction, bounded summaries/hashes, debug opt-in, never log auth/thinking by default |
| R-26 | Один bad aggregate обрушает tick | Medium | High | Repeating exception per tick | Typed failures, per-work-item isolation, rate-limited log, circuit/suspend bad item |
| R-27 | Core removal случайно удаляет деревню | Medium | Critical | Records disappear after grief/explosion | `ORPHANED` lifecycle, explicit recover/delete, binding audit |
| R-28 | Definition/datapack removed after save | Medium | High | Missing profession/template id | Namespaced ids, `MISSING_DEFINITION` state, no crash/guess, explicit remap/migration |
| R-29 | Игровая «неожиданность» становится непредсказуемой порчей | Medium | High | Random priorities/world mutation | Surprise only in semantic proposals/soft selection; execution and safety deterministic |
| R-30 | Тесты зависят от живого Ollama | High | High | CI flaky/slow/no model | Fake scripted backend for CI; optional manual integration profile only |

## 3. Решения

### D-001 — SyntValley моделирует деревню, не колонию

Статус: `ACCEPTED`

Решение:

- Жители имеют собственные needs, работу, память, характер и инициативу.
- Игрок участвует, помогает и наблюдает; обычный gameplay не сводится к выдаче каждой команды сверху.
- Administrative priority management отделено и creative/permission-only.

Почему: это главный продуктовый дифференциатор. Архитектура task/project может напоминать общие симуляционные паттерны, но UX и ownership решений ориентированы на автономную деревню.

Последствия: нужны explainable decision log, deterministic fallback и инициативы, возникающие из событий. Одного LLM-чата недостаточно.

Пересмотреть: не предполагается без изменения концепции продукта.

### D-002 — Собственная Citizen entity, не прямое расширение vanilla villager Brain

Статус: `ACCEPTED`

Решение: `SyntCitizenEntity` строится как собственная mob entity, ожидаемо на базе `PathfinderMob`; vanilla navigation/living primitives переиспользуются, но доменная модель не наследует `Villager`/`AbstractVillager` и не управляется напрямую `Brain`/gossip/trade/POI lifecycle.

Почему не vanilla Villager Brain напрямую:

- Brain несёт набор предположений vanilla villager о schedule, memories, activities, professions, POI, gossip и размножении.
- Эти semantics не совпадают с stable domain identity, explicit tasks/projects, LLM decisions и деревенской памятью SyntValley.
- Скрытая связь с vanilla AI затруднит save reconciliation, explainability и bounded scheduling.
- Попытка одновременно позволить Brain и SyntValley task engine управлять movement/activity создаст конкурирующих owners поведения.

Что переиспользуем: базовые entity lifecycle, attributes, navigation/pathfinding, goal selector для локальных mechanics, sounds/animation patterns и общие Minecraft APIs.

Цена: больше собственного AI/task кода и ответственность за поведение mob. Это осознанно.

Пересмотреть: только после prototype evidence, что отдельный строго локальный vanilla behavior можно адаптировать без передачи ownership Brain; не пересматривать whole-domain inheritance.

### D-003 — Hybrid AI: LLM для смысла, Java для истины и исполнения

Статус: `ACCEPTED`

Решение:

- LLM: dialogue, intentions, goal/project proposals, memory wording, social tone, rare strategy.
- Java: needs, pathfinding, inventories, resources, task states, placement, breaking, safety, lifecycle, save, network, budgets, fallback.

Почему: LLM недетерминирована, медленна и не имеет надёжного актуального world model. Java хорошо решает проверяемые правила; LLM добавляет разнообразие там, где exact optimality не нужна.

Последствия: нужно поддерживать semantic boundary и не протаскивать низкоуровневые tool calls «для удобства».

Пересмотреть: отдельные action types после threat/performance review и полного Java validator, но не принцип authority.

### D-004 — LLM не управляет блоками напрямую

Статус: `ACCEPTED`

Решение: protocol не содержит `place_block`, `break_block`, coordinates, commands или generic tool invocation. LLM может только `propose_project` по allowlisted template/purpose/zone hint.

Почему:

- snapshot устаревает до ответа;
- модель не знает claims, collisions, inventories, physics и соседние mods;
- одна ошибочная координата/selector может испортить мир;
- валидировать произвольный список block operations почти означает написать генератор/планировщик всё равно;
- большие block plans раздувают prompt/response и save.

Последствия: Java placement planner и staged task executor обязательны. «Творчество» выражается выбором проекта/приоритета, не произвольной геометрией v1.

Пересмотреть: только как отдельное исследование sandboxed blueprint generation в изолированном preview, никогда как прямой live-world executor.

### D-005 — На старте нет генеративного строительства

Статус: `ACCEPTED`

Решение: дом, ферма, склад и мастерская строятся по versioned templates; Java алгоритмически выбирает placement/rotation и валидирует footprint/resources/protection.

Почему:

- template делает bill of materials, progress, resume и тесты конечными;
- можно повторно проверить каждый stage/block;
- качество построек предсказуемо;
- проще обеспечить совместимость и recovery;
- генеративная геометрия не нужна, чтобы доказать живость деревни.

Цена: меньше визуального разнообразия. Компенсация — несколько шаблонов/вариантов/rotations и data-driven expansion.

Пересмотреть: после стабильного template project pipeline, UX preview/approval и protection adapters.

### D-006 — Server-authoritative с первого slice

Статус: `ACCEPTED`

Решение: integrated singleplayer использует logical-server application path; UI получает DTO/payload, client requests валидируются как в multiplayer.

Почему: перенос client-mutating prototype в multiplayer потребовал бы переписать identity, permissions, UI state и LLM ownership. Это именно тот долг, которого избегают vertical slices.

Последствия: даже простые screens требуют network/read-model boundary. Dedicated server tests начинаются рано.

Пересмотреть: не предполагается.

### D-007 — Bounded rare async LLM

Статус: `ACCEPTED`

Решение: fixed concurrency, bounded global queue, per-subject in-flight/cooldown, deadlines, bounded retries, circuit breaker и overload fallback. Ни одного LLM call per tick/citizen.

Почему: локальный Ollama latency измеряется секундами/десятками секунд, а server tick budget — миллисекундами. Недоступный backend не должен увеличивать RAM/threads без предела.

Последствия: модель не участвует в реакциях, требующих tick latency. UI показывает pending/busy/fallback состояния.

Пересмотреть: численные defaults по profiling; принцип bounded/async не пересматривается.

### D-008 — `SavedData` как единственный canonical world store

Статус: `ACCEPTED`

Решение: normalized versioned state в Overworld `SyntValleySavedData`; local block/entity NBT только binding; JSON definitions/explicit export не являются canonical save.

Почему:

- штатная интеграция с world lifecycle/backup;
- `setDirty()` и save hooks лучше ручной записи множества файлов;
- одна транзакционная граница ссылок;
- cross-dimension state удобно индексировать из стабильной Overworld storage.

Почему не JSON-per-citizen: файловый storm, сложная атомарность cross-record updates, собственная recovery/locking логика, риск записи каждый tick.

Последствия: canonical save не человекочитаем напрямую; нужен admin JSON export и хорошие codec/migration tools.

Пересмотреть: если доказано, что root size/world save latency неприемлемы. Возможное шардирование должно сохранить один repository contract и транзакционные/ref integrity rules.

### D-009 — Deferred dirty queue материализует state, disk write остаётся Minecraft

Статус: `ACCEPTED`

Решение: domain mutations coalesce dirty aggregate keys; periodic/pre-save/stop flush обновляет `SavedData` и ставит dirty marker. Мод не пишет canonical file в каждом tick и не вызывает полный world save ради mutation.

Почему: требуются bounded per-tick costs и штатная disk semantics.

Пересмотреть: cadence/batch sizes по measurement. Нельзя убирать pre-save full drain.

### D-010 — Stable domain ID отличается от entity UUID/runtime id

Статус: `ACCEPTED`

Решение: `CitizenId`/`VillageId` — typed UUIDs; Minecraft entity UUID хранится как текущий binding, integer entity id transient.

Почему: entity может unload/recreate, а личность/память/задача должны пережить lifecycle. Binding conflicts должны быть обнаружимы.

Последствия: нужен reconciler/generation/quarantine. Нельзя искать Citizen по display name/позиции.

Пересмотреть: не предполагается.

### D-011 — Нормализованный world state вместо serialization object graph

Статус: `ACCEPTED`

Решение: явные persistent records/refs и codecs. Futures, goals, paths, entities, caches и locks не сериализуются.

Почему: стабильная schema, bounded load, migration и безопасный restart требуют отделения runtime.

Последствия: после load нужен reconciliation; это feature, не дефект.

Пересмотреть: конкретные fields, не принцип.

### D-012 — Task machine и resource reservations принадлежат Java

Статус: `ACCEPTED`

Решение: LLM intent/project proposal компилируется application layer в Java tasks; task steps повторно проверяют world. Resource ledger — cache, inventory — истина.

Почему: мир меняется между plan/execute из-за игроков, других жителей и mods.

Последствия: stale outcomes/replan являются штатными; задачи обязаны быть идемпотентными или проверять postcondition.

Пересмотреть: нет для authority; алгоритмы планирования можно сменить.

### D-013 — Loaded chunks only в ранних версиях

Статус: `ACCEPTED`

Решение: полноценное entity/task/world execution только в loaded chunks. Мод не force-load’ит chunks. Unload переводит работу в pause/replan.

Почему: честная симуляция path/inventory/block state требует загруженного мира; force-loading влияет на performance/server policy.

Последствия: автономность зависит от естественной/внешней chunk loading. Domain хранит `lastSimulatedGameTime` и seam будущего coarse mode.

Пересмотреть: future offline coarse simulator с отдельными ограничениями; не скрытая full entity simulation.

### D-014 — Personality влияет на soft scores, не hard rules

Статус: `ACCEPTED`

Решение: traits/mood меняют веса необязательных вариантов в bounded диапазоне. Hunger emergency, safety, permissions и feasibility не обходятся.

Почему: характер должен создавать различия, но не превращать систему в неотлаживаемый random executor.

Последствия: policy tests проверяют monotonic/bounds; diversity оценивается статистически.

Пересмотреть: веса по playtests.

### D-015 — Память структурирована и ограничена

Статус: `ACCEPTED`

Решение: memory record содержит kind/summary/source/facts/participants/salience/time; full transcripts/thinking не сохраняются. Есть per-owner/global caps, dedupe/decay/pinning limits.

Почему: token/save/privacy/performance. Источник нужен, чтобы не считать hallucination фактом.

Последствия: забывание — часть дизайна. Важные observed events могут получать protected bounded slots.

Пересмотреть: retention values и категории.

### D-016 — Отдельные интерфейсы для chat, overview, priorities, log, debug

Статус: `ACCEPTED`

Решение: Citizen Chat открывается entity interaction; Village Overview/priority/log — Village Console; debug permission-gated. Overview не маскируется под разговор.

Почему: разные authority/data density/context. Это снижает prompt/UI coupling и делает multiplayer checks ясными.

Пересмотреть: визуальная навигация/вкладки, не security/context boundary.

### D-017 — Root package `dev.syntvalley`

Статус: `ACCEPTED`

Решение: использовать `dev.syntvalley`, пока у проекта нет подтверждённого reverse-domain namespace.

Почему: не заявлять чужой/непринадлежащий домен через `com.*`; сохранить короткий устойчивый namespace.

Пересмотреть: до публичного API/save/network release при появлении официального домена. После релиза rename дорог и требует migration/compatibility review.

### D-018 — Один Gradle module на старте, строгие package rules

Статус: `ACCEPTED`

Решение: не создавать множество artifacts/subprojects до фактической необходимости. Boundaries закрепить package dependencies и architecture tests.

Почему: NeoForge registration/resources/runs проще в одном module; физические modules не гарантируют хорошую архитектуру сами.

Пересмотреть: если build time, reusable pure-Java simulation tests или backend separation дадут измеримую пользу.

### D-019 — LLM backend port, Ollama как первый adapter

Статус: `ACCEPTED`

Решение: application знает `LlmBackend`/backend-neutral request-result; `OllamaLlmBackend` реализует HTTP structured outputs. Protocol/domain не зависят от Ollama JSON envelope.

Почему: future backend replacement и fake CI backend; Ollama API не строго versioned.

Последствия: adapter должен переводить metrics/errors и не протаскивать vendor DTO.

Пересмотреть: расширять adapters, не убирать port.

### D-020 — CI не зависит от живой модели

Статус: `ACCEPTED`

Решение: scripted/fake backend покрывает success/timeout/malformed/stale/overload. Реальный Ollama — opt-in integration/manual profile.

Почему: воспроизводимость, скорость, отсутствие модели/железа в CI.

Последствия: нужны contract fixtures и периодический реальный compatibility test отдельно.

Пересмотреть: не предполагается.

### D-021 — Ошибка decode создаёт quarantined SavedData, а не пустой world state

Статус: `ACCEPTED` в Slice 2

Решение: deserializer `SyntValleySavedData` перехватывает unsupported future schema, migration failure и codec corruption внутри factory и возвращает read-only quarantined instance с исходным root tag и bounded diagnostic. Gameplay runtime для такого state не запускает mutations, instance не помечается dirty и при непредвиденном вызове `save` возвращает defensive copy исходного root.

Почему: фактический `DimensionDataStorage` Minecraft/NeoForge 1.21.1 перехватывает исключение deserializer и `computeIfAbsent` после этого создаёт новый пустой state. Простое `throw` из codec поэтому не является fail-closed и может привести к перезаписи future/corrupt save после первой mutation.

Последствия: мир и сервер могут загрузиться для диагностики, но SyntValley content остаётся persistence-disabled с ясным error log. Автоматический repair/drop records запрещён; восстановление требует совместимой версии или отдельного opt-in repair flow.

Пересмотреть: если закреплённая версия storage API получит официальный способ различать missing data и decode failure без quarantine object.

### D-022 — В 1.21.1 save correctness не зависит от `LevelEvent.Save`

Статус: `ACCEPTED` в Slice 2

Решение: успешный repository commit сначала заменяет canonical in-memory root и немедленно вызывает `SavedData#setDirty()`. Periodic cadence обслуживает bounded dirty bookkeeping, `ServerStoppingEvent` выполняет full drain до shutdown save, а `LevelEvent.Save` используется только как post-save verification/metrics hook и не считается pre-save callback.

Почему: в фактическом `ServerLevel#save` версии 1.21.1 `getDataStorage().save()` вызывается до публикации `LevelEvent.Save`. Обновлять единственную копию state или ставить первый dirty marker в этом event слишком поздно для текущего save cycle.

Последствия: latest aggregate revision сохраняется штатным Minecraft save независимо от очереди bookkeeping. Post-save hook не создаёт новую mutation только ради `last_flush_game_time`; этот metadata marker обновляется при реальном periodic/stop drain.

Пересмотреть: при смене закреплённой Minecraft/NeoForge версии после проверки реального порядка hooks.

## 4. Решения, отложенные до соответствующего slice

### P-001 — Конкретный NeoForge/ModDevGradle patch version

Статус: `ACCEPTED` в Slice 1

Закреплены версии из официального `MDK-1.21.1-ModDevGradle`: NeoForge `21.1.235`, ModDevGradle `2.0.141`, Gradle Wrapper `9.2.1`, Parchment `2024.11.17`; Java toolchain и compile release — `21`. Версии не плавающие. Upgrade требует отдельной проверки build, unit/Game Tests и dedicated server.

### P-002 — Jackson vs Minecraft codecs для LLM JSON

Статус: `PROPOSED`

Критерии: strict duplicate/unknown field handling, bounded parse, Java 21/NeoForge packaging, JSON schema generation/support и shading/dependency risk. Выбор не должен позволить reflection-based polymorphic action dispatch.

### P-003 — Protection mod integration API

Статус: `PROPOSED`

До adapters default building policy должна быть conservative: только разрешённая village zone, expected replaceable blocks и no protected/unknown destructive overwrite. Конкретные integrations — отдельные optional adapters.

### P-004 — Hiring economy

Статус: `PROPOSED`

Архитектурно `HireCitizenCommand`/contract item, но recipe/cost/rarity — game design tuning. Не связывать identity creation с breeding.

### P-005 — Project approval policy

Статус: `PROPOSED`

Выбрать, какие safe template projects auto-accept, какие требуют player confirmation, и как это работает без online player. Policy должна быть server-side и persistent.

### P-006 — Offline coarse simulation

Статус: `PROPOSED`, вне ранних версий

Нужны отдельные invariants, reconciliation, anti-duplication и gameplay limits. До этого `lastSimulatedGameTime` не означает автоматическое производство за offline interval.

## 5. Анти-решения: что нельзя вводить незаметно

- `CompletableFuture.join/get` на server thread для LLM/диска.
- `newCachedThreadPool`, unbounded `LinkedBlockingQueue` или thread-per-citizen.
- `Map<UUID, Citizen>` static global как world store.
- Universal JSON action dispatcher с class/action name от модели/client.
- Полный world/entity NBT в prompt или packet.
- Client-only screen, который напрямую меняет integrated server object.
- JSON save per tick/per citizen.
- Два authoritative save источника с merge «потом».
- Автоматический drop unknown/corrupt Village/Citizen record.
- Проектный temporary NPC на базе vanilla villager с обещанием «заменим позже».
- Tests, которые проходят только при доступном локальном `qwen3:8b`.
- Использование display name/position/runtime entity id как identity.
- Повтор world-mutating task step без проверки postcondition.

## 6. Процесс изменения решения

1. Зафиксировать наблюдаемую проблему/метрику, а не только предпочтение.
2. Указать затронутые invariants, saves, network, protocol и existing worlds.
3. Сравнить минимум два варианта и migration cost.
4. Обновить status/новую decision record; старую пометить `SUPERSEDED`, не стирать историю.
5. Обновить `PROJECT_SPEC.md`, `ARCHITECTURE.md`, protocol/save/multiplayer docs и slices при необходимости.
6. Добавить acceptance/tests для нового риска.
7. Только затем менять код.
