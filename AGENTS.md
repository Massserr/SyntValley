# AGENTS.md — правила работы в SyntValley

Область действия: весь репозиторий. Более вложенный `AGENTS.md`, если появится, может уточнять локальные команды, но не отменяет server-authority, save/protocol safety и scope текущего slice.

## 1. Контекст проекта

- Minecraft: `1.21.1`.
- Loader: NeoForge `21.1.235` для `1.21.1`.
- Java: `21`.
- Build: ModDevGradle `2.0.141`, Gradle Wrapper `9.2.1`.
- Mod id: `syntvalley`.
- Root package: `dev.syntvalley`.
- Первый LLM backend/model: Ollama / `qwen3:8b`.
- SyntValley — живая деревня, не копия MineColonies и не расширение vanilla Villager AI.
- AI model: LLM предлагает редкие semantic intents/dialogue; Java валидирует и исполняет.
- Canonical gameplay state: logical server.
- Разработка: один явно запрошенный slice из `DEVELOPMENT_SLICES.md` за задачу.

## 2. Обязательное чтение перед изменением

Всегда прочитать:

1. `PROJECT_SPEC.md`.
2. `ARCHITECTURE.md`.
3. `DEVELOPMENT_SLICES.md`, особенно запрошенный slice.
4. `RISKS_AND_DECISIONS.md`.

Дополнительно:

- save/persistence/entity lifecycle → `SAVE_FORMAT.md`;
- LLM/prompt/action/parser → `LLM_ACTION_PROTOCOL.md`;
- payload/UI/server authority → `MULTIPLAYER_PLAN.md`.

Перед редактированием выполнить `git status --short`, найти реальные файлы через `rg --files` и изучить соседние тесты/паттерны. Репозиторий может содержать пользовательские незавершённые изменения: не стирать и не переформатировать unrelated code.

## 3. Scope работы

- Реализовывать только прямо запрошенный slice/system/fix.
- Не начинать следующий slice «пока здесь».
- Не создавать throwaway MVP, временную entity/storage/network/LLM architecture с обещанием заменить позже.
- Если prerequisite отсутствует, сначала сообщить точный blocker или реализовать prerequisite только когда он входит в запрошенный slice.
- Если код требует изменить contract, сначала обновить соответствующий документ и добавить/изменить decision в `RISKS_AND_DECISIONS.md`.
- Не менять mod id, root package, save/protocol/network version и public identifiers без migration/compatibility review.

## 4. Непереговорные запреты

- Не копировать код, package/class/file layout или ошибки MineColonies/AIMineColonies. Старый проект допустим только как источник общих идей/антипримеров, если пользователь явно дал его для анализа.
- Не наследовать Synt Citizen от `Villager`/`AbstractVillager` и не отдавать domain/task ownership vanilla Brain без нового принятого решения.
- Не разрешать LLM прямые block/item/entity/command operations.
- Не вводить generic `execute(actionName, arbitraryJson)`, reflection-based action dispatch или class name из LLM/client input.
- Не блокировать server thread HTTP, file I/O, `Future#get/join`, sleep или долгим parse.
- Не использовать unbounded executor/queue/cache/log/memory/history/list/packet.
- Не хранить gameplay world state в static mutable map/singleton.
- Не обращаться к `Level`, `Entity`, `BlockEntity`, registry holder, container или mutable aggregate из LLM/background thread.
- Не писать canonical JSON/save-файлы каждый tick; не создавать второй authoritative save.
- Не доверять client-supplied player identity, creative flag, coordinates, revision, permission или target.
- Не импортировать `net.minecraft.client.*` из common/server packages.
- Не force-load chunks в ранних slices.
- Не добавлять полноценную offline simulation, families, raids, voice или generative building вне отдельного утверждённого scope.
- Не логировать auth headers, secrets, full prompts, raw unlimited responses или thinking/chain-of-thought.

## 5. Архитектурные правила кода

### Domain и application

- `domain..` — Java value types, aggregates, state machines и policies; без NeoForge/Minecraft/Ollama/client dependencies.
- `application..` — commands, queries, services, schedulers и ports; world/backend/network конкретика приходит adapters.
- Использовать typed IDs (`VillageId`, `CitizenId`, `TaskId`, etc.), а не голые UUID/string в domain APIs.
- Любая mutation выполняется на logical server через application service/repository с expected revision/invariants.
- Domain возвращает typed result/event; не отправляет packet, не пишет save и не вызывает LLM.

### Minecraft/NeoForge

- Проверять API/события по документации/source именно ветки 1.21–1.21.1; newer NeoForge snippets не считать совместимыми.
- Регистрация через NeoForge recommended deferred registries для закреплённой версии.
- Gameplay при наличии `Level` выполняется только когда `isClientSide()` false.
- Client registration/render/screens находятся в client-only entry point/package.
- Entity/block entity NBT хранит local binding; canonical Village/Citizen state находится в world repository.
- World-mutating task step повторно проверяет loaded chunk, current state, protection, resources и postcondition непосредственно перед изменением.

### Persistence

- Следовать `SAVE_FORMAT.md`: Overworld `SyntValleySavedData`, explicit schema/codec/migrations.
- Mutation commit атомарно обновляет current in-memory `SavedData` record → dirty key + `setDirty()` marker; periodic/pre-save/stop flush обслуживает bounded bookkeeping, а не хранит единственную копию нового состояния.
- Нельзя молча загружать future schema как empty state или отбрасывать corrupt Village/Citizen record.
- Runtime paths, futures, leases, queues, entity handles и LLM pending requests не сериализуются.
- Любое новое persistent field требует round-trip/bounds/default/migration review и fixture test.

### Networking/UI

- Следовать `MULTIPLAYER_PLAN.md`: directional `CustomPacketPayload`, explicit `StreamCodec`, bounded DTO.
- Screen получает server read model; не читает integrated server object.
- C2S handler: structural bounds → authenticated sender → rate/session/context/distance → permission → target/revision → application command.
- Creative-only action проверяется server-side при каждой mutation.
- Snapshot/delta имеют revisions; lists/logs paginated; subscriptions очищаются при close/disconnect.

### LLM

- Следовать `LLM_ACTION_PROTOCOL.md` буквально.
- `LlmBackend` backend-neutral; Ollama envelope остаётся adapter detail.
- Fixed executor + bounded queue/inbox + deadline/retry cap/circuit/cooldown/quotas.
- Snapshot строится на server thread и становится immutable/bounded.
- Response: strict body/JSON/schema/correlation/capability/ref/staleness/policy/world validation.
- Structured output не заменяет Java validation.
- Unknown/unsafe action fail closed; один optional syntax repair максимум; deterministic fallback обязателен.
- Completion применяется только на server thread и только к тому же runtime generation/pending request/revision.

## 6. Стиль Java

- Java 21, UTF-8, без preview features, если они отдельно не включены и не обоснованы.
- Предпочитать небольшие immutable `record`/value objects для DTO/IDs/results; mutable state инкапсулировать в aggregate/repository.
- Избегать raw types, unchecked casts и nullable magic. Optional использовать на API boundaries осмысленно, не как поле каждой hot object без причины.
- Enum/string switches допустимы для закрытых domain states; data-driven definitions используют namespaced IDs.
- Исключения не являются обычным domain control flow. Ожидаемые отказы — sealed/typed result/reason code.
- Не ловить `Exception` с пустым body. Async exceptions обязательно observe/map/log rate-limited.
- Не добавлять абстракцию без текущего consumer/test, кроме ports/boundaries уже требуемых целевой архитектурой.
- Комментарии объясняют invariant/почему, не пересказывают строку кода.
- Public/save/network/protocol constants и bounds имеют тест/документацию.

## 7. Concurrency checklist

Для каждого async изменения ответить в code/review:

- Кто владеет executor и кто его закрывает?
- Каков hard queue/in-flight/result cap?
- Что происходит при overload?
- Каковы connect/request deadlines и retry cap?
- Есть ли runtime generation/cancellation при server stop?
- Какие immutable данные уходят off-thread?
- Где completion возвращается на server thread?
- Как проверяется staleness/idempotency?
- Как исключены raw Minecraft objects и hidden common pool?

Если на любой вопрос нет ответа, async часть не готова.

## 8. Build и test commands

На Windows используйте project helper: он находит настоящий JDK 21, задаёт `JAVA_HOME` только для дочернего процесса и временно подключает project/Gradle cache как ASCII `SUBST` paths. Последнее обязательно на профилях с кириллицей: Java argument files Gradle test/GameTest workers иначе могут получить повреждённый classpath.

```powershell
.\gradlew-jdk21.ps1 build
.\gradlew-jdk21.ps1 test
.\gradlew-jdk21.ps1 runGameTestServer
.\gradlew-jdk21.ps1 runClient
.\gradlew-jdk21.ps1 runServer
```

Если профиль и workspace имеют ASCII paths и `JAVA_HOME` уже указывает на JDK 21, стандартный `./gradlew`/`.\gradlew.bat` эквивалентен. CI использует `./gradlew build` после `actions/setup-java` с Java 21.

- `build` и требуемые automated tests запускать после каждого implementation slice.
- `runGameTestServer` обязателен, когда добавлены/изменены Minecraft interactions.
- `runServer` smoke обязателен для registries/entity/UI/network/client separation.
- `runClient` нужен для визуальной/manual UI проверки, но не заменяет tests.
- Real Ollama test только opt-in/manual; CI использует fake backend.
- Не утверждать, что команда прошла, если она не запускалась или завершилась timeout/failure.

## 9. Требования к тестам

- Bug fix сначала получает regression test, если воспроизводим на соответствующем уровне.
- Domain policies/state machines: fast unit/property tests.
- Save/network/protocol codecs: round-trip, golden fixtures, invalid types/unknown/duplicate/size/depth/bounds.
- World interactions: Game Tests с минимальными structure fixtures.
- Client/server separation: dedicated startup smoke.
- LLM: scripted fake success/timeout/malformed/large/stale/replay/overload/late completion; живой backend не gate.
- Scheduler/concurrency: fake clock, deterministic seed, saturation/shutdown/race tests без flaky sleeps.
- Multiplayer: forged session/target/permission/revision/rate и multi-client isolation по slice matrix.
- Performance statements подтверждать measurement/soak, а не только отсутствием exception.

## 10. Порядок выполнения задачи

1. Уточнить requested slice/outcome из user request без расширения scope.
2. Прочитать contracts и проверить repo/dirty state.
3. Составить краткий план по acceptance criteria.
4. Сначала добавить/скорректировать tests/contracts, если они определяют boundary.
5. Реализовать минимальный полный vertical path через окончательные layers.
6. Запустить targeted tests, затем required build/Game Tests/smoke.
7. Просмотреть diff на side leaks, unbounded state, save/protocol changes, unrelated formatting и generated files.
8. Сообщить результат: что работает, какие файлы/контракты изменены, точные команды/результаты, что сознательно не сделано по non-goals, оставшиеся риски.

## 11. Работа с документацией и версиями

- Если вопрос зависит от текущей NeoForge/Ollama API, использовать первичную официальную документацию/source; ссылки сохранять рядом с архитектурным утверждением при material change.
- Документация NeoForge 1.21–1.21.1 upstream архивная; не обновлять код на latest Minecraft API незаметно.
- Не угадывать dependency versions. Pin проверенные версии в build и объяснить upgrade отдельно.
- При расхождении docs и компилируемого mapped API приоритет у фактической закреплённой dependency/source; contract обновляется с записью решения.

## 12. Git и файлы

- Сохранять пользовательские изменения; не использовать `git reset --hard`, destructive checkout или массовое удаление.
- Не коммитить `run/`, worlds, logs, crash reports, IDE state, credentials, Ollama data/models, build outputs.
- Generated resources менять через их source/datagen, если pipeline уже существует; не перетирать вручную unrelated generated output.
- Не выполнять repository-wide formatting без прямого запроса.
- Новые assets должны быть оригинальными/лицензионно допустимыми; vanilla-inspired не означает копирование чужого mod asset.

## 13. Review gate перед завершением

- Scope соответствует одному slice?
- Server является единственным authority?
- Dedicated server не импортирует client classes?
- Все queues/lists/text/packets bounded?
- Нет Minecraft objects off-thread?
- LLM/client input не попадает в generic executor?
- World mutation revalidated at execution?
- Save dirty/migration/restart semantics покрыты?
- Replay/stale response idempotent?
- Tests действительно запускались и соответствуют acceptance?
- Non-goals не реализованы скрыто?

При отрицательном ответе работа не готова к передаче.
