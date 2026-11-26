# AurumAuction — руководство

Версия 0.7.0. Paper/Purpur/Folia 1.16.5 – 1.21.x и 26.x, Java 8+ (сервер), Vault + любой плагин экономики.

## Установка

1. Положите `AurumAuction-0.7.0.jar` в `plugins/`.
2. Установите **Vault** и плагин экономики (EssentialsX, CMI, XConomy и т.п.).
3. Запустите сервер: создадутся `config.yml`, `gui.yml`, `messages.yml`, `lots.yml`, `payouts.yml`.
4. Проверьте `/ah` и `/ah admin info 1`.

Без Vault плагин включается, но торговля отключена (в логе `economy mode: UNAVAILABLE`).

## Команды

| Команда | Право | Описание |
|---|---|---|
| `/ah` | `auction.use` | Открыть аукцион |
| `/ah sell <цена> [кол-во]` | `auction.sell` | Выставить предмет из руки; `100kk`, `1.5kkk`, `2 000` |
| `/ah sellmenu <цена>` | `auction.sell` | Выставить стак из инвентаря через меню |
| `/ah buy <id> [кол-во]` | `auction.use` | Купить лот или его часть |
| `/ah auction <старт> [часы] [выкуп]` | `auction.sell` | Лот со ставками; `выкуп` опционален |
| `/ah bid <id> <сумма>` | `auction.use` | Сделать ставку (деньги резервируются) |
| `/ah claims` | `auction.use` | Забрать выигранные лоты |
| `/ah history` | `auction.use` | GUI истории покупок/продаж |
| `/ah top [sellers\|buyers]` | `auction.use` | Топ продавцов/покупателей |
| `/ah watch <текст>` | `auction.use` | Подписка на новые лоты; `list`, `remove`, `clear` |
| `/ah search <текст>` | `auction.use` | Поиск по названию, материалу или продавцу |
| `/ah my` | `auction.use` | Мои активные лоты |
| `/ah expired` | `auction.use` | Просроченные лоты (забрать предмет) |
| `/ah help` | — | Справка |
| `/ah admin info <id>` | `auction.admin` | Карточка лота |
| `/ah admin remove <id>` | `auction.admin` | Снять лот; активный/просроченный вернётся продавцу |
| `/ah admin end <id>` | `auction.admin` | Принудительно завершить лот (ставки/истечение) |
| `/ah admin autobuy` | `auction.admin` | Запустить авто-выкуп вручную |
| `/ah admin payouts` | `auction.admin` | Повторить выплаты и показать очередь |
| `/ah admin reload` | `auction.admin` | Перезагрузить конфиги |

Алиасы: `/auction`, `/аукцион`.

## Права

| Право | По умолчанию | Что даёт |
|---|---|---|
| `auction.use` | true | Открывать аукцион, искать, смотреть свои лоты |
| `auction.sell` | true | Выставлять предметы |
| `auction.buy` | true | Покупать (зарезервировано) |
| `auction.tax.bypass` | false | Не платить налог с продажи |
| `auction.bypass.blocked` | false | Выставлять предметы из блок-листа |
| `auction.group.<name>` | false | Группа налога/лимита |
| `auction.limit.<n>` | false | Лимит лотов; берётся максимальное выданное |
| `auction.admin` | op | Админ-команды |

Пример: `auction.limit.15` даёт 15 лотов вместо `lots.default-limit`.

## Плейсхолдеры (PlaceholderAPI)

- `%aurumauction_lots%` — всего активных лотов;
- `%aurumauction_mylots%` — активные лоты игрока;
- `%aurumauction_expired%` — просроченные лоты игрока;
- `%aurumauction_sold%` — проданные лоты игрока.

## config.yml

| Ключ | По умолчанию | Описание |
|---|---|---|
| `locale` | `ru` | `ru`/`en`; влияет на стартовый `messages.yml` |
| `storage.type` | `YAML` | `YAML` / `SQLITE` / `MYSQL` |
| `storage.autosave-seconds` | 60 | Период записи (YAML) |
| `storage.migrate-yaml` | true | Импорт `lots.yml` в пустую SQL-таблицу |
| `storage.sqlite.file` | `lots.db` | Файл SQLite |
| `storage.mysql.*` | — | host/port/database/user/password/table-prefix |
| `economy.mode` | `VAULT` | Vault-экономика |
| `economy.currency-format` | `#,##0.##` | Формат, если Vault не умеет `format` |
| `lots.expire-hours` | 48 | Срок жизни лота |
| `lots.min-price` / `max-price` | 1 / 1e9 | Границы цены |
| `lots.default-limit` | 5 | Лимит лотов без прав |
| `lots.max-amount-per-lot` | 64 | Максимум предметов в лоте |
| `lots.allow-shulkers` | true | Разрешить шалкеры |
| `lots.tax-percent` | 5.0 | Налог с продажи |
| `lots.tax-groups` | — | Налог по `auction.group.<name>` |
| `lots.max-total` | 0 | Общий лимит лотов (0 — без) |
| `lots.buy-confirm` | true | Подтверждение покупки |
| `lots.retention-days` | 30 | Чистка SOLD/CANCELLED (0 — хранить вечно) |
| `lots.blocked-materials` | список | Запрещённые предметы |
| `search.min-length` | 2 | Минимум символов для поиска |
| `search.input-timeout-seconds` | 30 | Ожидание ввода из GUI |
| `limits.click-cooldown-ms` | 300 | Анти-спам кликов |
| `limits.command-cooldown-ms` | 500 | Анти-спам `/ah sell` и `/ah search` |
| `limits.groups` | — | Лимит лотов по группе |
| `logging.transactions` | true | Лог `transactions.log` |
| `payouts.retry-seconds` | 300 | Повтор неудачных выплат |
| `bids.enabled` | true | Включить ставки |
| `bids.min-increment` | 1.0 | Минимальный шаг ставки |
| `bids.min-increment-percent` | 5.0 | Шаг в % от текущей ставки |
| `bids.extend-seconds` | 60 | Anti-snipe продление |
| `bids.default-hours` / `max-hours` | 24 / 168 | Длительность аукциона |
| `auto-buy.enabled` | true | Авто-выкуп зависших fixed-лотов |
| `auto-buy.after-hours` | 168 | Через сколько часов выкупать |
| `auto-buy.percent` | 70.0 | Процент цены продавцу |
| `auto-buy.check-seconds` | 3600 | Период проверки |
| `claims.auto-claim` | true | Авто-выдача выигранных лотов при входе |
| `notifications.enabled` | true | Офлайн-уведомления |
| `history.size` | 200 | Записей истории на игрока |
| `history.top-size` | 10 | Размер топа |
| `watch.max` | 5 | Подписок на игрока |
| `multi-server.enabled` | false | Требует MySQL; общая таблица лотов |
| `multi-server.server-id` | 1 | Уникальный 0–1023 (snowflake ID) |
| `multi-server.sync-seconds` | 5 | Период cross-server watch polling |
| `bedrock.max-lore-lines` | 8 | Обрезка lore для Floodgate (0 — выкл.) |
| `retention-check-seconds` | 3600 | Период чистки |
| `expire-check-seconds` | 60 | Период перевода лотов в EXPIRED |

## gui.yml

- `main.title` — заголовок, плейсхолдеры `%page%`, `%pages%`, `%total%`, `%query%`, `%category%`.
- `main.rows` — 2–6 рядов.
- `lot-lore` — лор лота; плейсхолдеры `%id%`, `%seller%`, `%price%`, `%unit%`, `%amount%`, `%time%`, `%category%`, `%item%`.
- `shulker-lore` — блок предпросмотра шалкера (`%contents%`).
- `buttons.*` — слот, материал, имя. Кнопки: `previous`, `next`, `search`, `category`, `sort`, `mine`, `expired`, `close`, `confirm`, `cancel`.
- `filler` — материал заполнителя.
- `confirm.*` — окно подтверждения: заголовок, ряды, лор.

Кнопки можно двигать по слотам, но не выносить за пределы инвентаря.

## Ставки

1. Продавец: `/ah auction <старт> [часы] [выкуп]` — предмет из руки уходит в лот со ставками.
2. Покупатель: `/ah bid <id> <сумма>` — сумма резервируется; предыдущая ставка возвращается.
3. Минимальный шаг: `bids.min-increment` или `bids.min-increment-percent` от текущей ставки.
4. За последние `bids.extend-seconds` секунд ставка продлевает лот.
5. Если указан выкуп, ставка ≥ выкупа завершает аукцион сразу.
6. По окончании: победитель получает предмет (или `/ah claims`), продавец — выплату с налогом.

## Авто-выкуп

Fixed-лоты, не проданные за `auto-buy.after-hours`, выкупает сервер:
продавцу начисляется `auto-buy.percent` от цены, лот закрывается как `CANCELLED`.
Вручную: `/ah admin autobuy`.

## История и рейтинги

- `/ah history` — GUI последних сделок: роль (покупка/продажа), предмет, сумма, налог,
  контрагент, дата. Хранится `history.size` записей на игрока (`history.yml`).
- `/ah top` — продавцы по сумме продаж; `/ah top buyers` — покупатели по расходам.
- `/ah watch <текст>` — подписка; при появлении лота, где встречается текст (название предмета,
  продавец, категория), приходит уведомление. `list` / `remove <текст>` / `clear`.

## Folia, мультисервер и Bedrock

- **Folia**: поддержка встроена (`folia-supported: true`), отдельный региональный планировщик.
  В логе при старте: `Scheduler: FOLIA (regionized)`.
- **Мультисервер**: включите `storage.type: MYSQL` и `multi-server.enabled: true`, задайте
  уникальный `server-id` (0–1023) на каждом сервере. Лоты видны мгновенно, продажа/выплата
  защищены атомарным переходом статуса, новые лоты для watchlist подтягиваются каждые
  `sync-seconds`. Все серверы должны использовать одинаковые БД и `table-prefix`.
- **Bedrock**: при установленном Floodgate игроки Geyser определяются автоматически; лор
  обрезается до `bedrock.max-lore-lines`. GUI и чат-поиск работают через стандартные события.

## Частичная покупка и sell-menu

- `/ah buy <id> [кол-во]` — купить часть стака; цена считается по цене за штуку, остаток лота
  остаётся активным. В GUI Shift+ЛКМ покупает 1 шт.
- `/ah sellmenu <цена>` — открывает меню: положите один стак, нажмите «Выставить». При закрытии
  меню предметы возвращаются в инвентарь.
- Группы: `auction.group.vip` и т.п. дают налог из `lots.tax-groups` и лимит из `limits.groups`.
- `auction.bypass.blocked` разрешает выставлять заблокированные материалы.

## Экономика

- Покупатель платит цену лота; продавец получает `цена − налог`.
- `auction.tax.bypass` у **онлайн-продавца** снимает налог на момент продажи.
- Если Vault не смог зачислить выплату (офлайн-сбой провайдера), сумма попадает в
  `payouts.yml` и повторяется каждые `payouts.retry-seconds`; вручную — `/ah admin payouts`.
- Лимит лотов считается по активным лотам; продажа/снятие освобождают слот.

## Хранилище и обслуживание

- **YAML**: `lots.yml` — атомарная запись (temp + move), автосейв и сохранение при остановке.
- **SQLITE**: `plugins/AurumAuction/lots.db`, схема и индексы создаются автоматически.
- **MYSQL**: таблица `<prefix>lots`; при первом запуске пустая таблица импортирует `lots.yml`
  и переименует его в `lots.yml.migrated`.
- `payouts.yml` — очередь невыплаченных средств; `claims.yml` — выигранные предметы;
  `notifications.yml` — офлайн-сообщения.
- `SOLD`/`CANCELLED` старше `retention-days` удаляются; `EXPIRED` не удаляются никогда,
  пока продавец не заберёт предмет.
- Перед обновлением делайте бэкап папки `plugins/AurumAuction`.

## FAQ

**«Экономика недоступна»** — нет Vault или его провайдера; проверьте `plugins/` и лог.

**«Этот предмет нельзя выставлять»** — материал в `lots.blocked-materials`.

**«Лимит лотов»** — снимите старые лоты или выдайте `auction.limit.<n>`.

**Купил, а предмета нет** — проверьте инвентарь: при полном инвентаре предмет выбрасывается рядом,
в чате будет предупреждение.

**Лот исчез после рестарта** — проверьте, что папка плагина не очищается и `lots.yml` на месте.

**Folia** — в 0.2.x не поддерживается: используется глобальный планировщик Bukkit.

## Сборка

```bat
set JAVA_HOME=D:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
"D:\Users\XuViGaN\apache-maven-3.9.9\bin\mvn.cmd" -o clean package
```

## E2E

`tools/e2e/e2e.js` — 17 проверок (продажа, поиск, GUI, подтверждение, покупка, налог,
отмена, админ-команды, персистентность). Запускается на Paper 1.16.5, 1.21.1 и 26.1.2;
на 26.3 боты не работают из-за протокола (нужен ViaBackwards, но движение всё равно ломается).
