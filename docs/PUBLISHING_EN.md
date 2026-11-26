# AurumAuction — English listing copy (Modrinth / SpigotMC)

## Basic settings

| Field | Value |
|---|---|
| Project name | `AurumAuction` |
| Summary (≤120) | `Auction house for Paper & Folia: lots, bids, buyouts, MySQL multi-server sync. Zero dependencies.` |
| Category | `Economy` |
| Environment | `Server` |
| Loaders | `Paper`, `Purpur`, `Folia`, `Spigot`, `Bukkit` |
| Game versions | `1.16.5` … `26.x` |

### Additional settings (step 3)

| Field | Value |
|---|---|
| Header link — Source | `https://github.com/xuviga/AurumAuction` |
| Header link — Support | `https://github.com/xuviga/AurumAuction/issues` |
| Header link — Documentation | `https://github.com/xuviga/AurumAuction/blob/main/docs/GUIDE.md` |
| License | `Custom` — paste the summary below, link `LICENSE` |
| Tags | `Supports Folia` only (not Addon / Library) |
| Keywords (5) | `auction`, `auction house`, `ah`, `market`, `economy` |

License summary (English):

> Proprietary license. You may install and use the compiled plugin on any number of Minecraft
> servers you own or operate, and modify the configuration files. Redistribution, publication,
> resale, sublicensing, decompilation, reverse engineering and derivative works require prior
> written permission from the author. Provided "as is", without warranty of any kind.

Sidebar section — **Requirements**:

> - Paper, Purpur or Folia 1.16.5 – 26.x (Spigot/Bukkit compatible)
> - Java 8 or newer
> - Vault + an economy plugin (EssentialsX, CMI, …) for trading
> - No other dependencies

Sidebar section — **Storage & network**:

> YAML works out of the box for a single server. Switch to SQLite for large lot counts, or MySQL
> with `multi-server.enabled: true` to share one auction across a network. Existing `lots.yml`
> is migrated automatically on first start.

### Summary alternatives

1. `Auction house for Paper & Folia: lots, bids, buyouts, MySQL multi-server sync. Zero dependencies.` (97)
2. `Auction house with fixed-price lots, bids, buyouts, SQL storage, multi-server sync and Folia support.` (101)
3. `Modern auction house: fixed-price lots, bids, buyouts, history, tax groups, MySQL sync, Folia & Bedrock.` (104)

---

## Description

# AurumAuction

**A lightweight auction house for Paper, Purpur and Folia — from 1.16.5 to 26.x.**

AurumAuction adds a complete, GUI-first auction house without dragging in dependencies, a database you
don't want, or a scheduler that breaks on Folia. One jar, YAML by default, SQLite or MySQL when you grow.

## Why AurumAuction

- **Zero dependencies.** No hard `depend` entries. Vault is wired through reflection and only used for money.
- **One jar for the whole network.** Start on YAML, switch to SQLite or MySQL with a config change —
  existing `lots.yml` is imported automatically.
- **Multi-server ready.** Snowflake lot IDs and atomic lot transitions keep two servers from selling
  the same item twice.
- **Folia-aware.** A dedicated scheduler adapter switches to region schedulers when Folia is detected
  (detection is by server brand, not by class presence).
- **Bedrock-friendly.** Item lore is trimmed for Geyser/Floodgate players, so tooltips stay readable.

## Features

### For players

- **Auction GUI** — categories, six sort modes (newest, price, unit price, name), pagination and search.
- **Fixed-price lots** and **bid lots** with a minimum increment, current leader and bid history.
- **Escrow bids** — money is taken when you bid and returned automatically when someone outbids you.
- **Anti-snipe** — a bid in the last 60 seconds extends the lot, so no last-second steals.
- **Buyout** — optional instant purchase on bid lots.
- **Partial purchases** — buy 8 of a stack of 64 with `/ah buy <id> 8` or Shift+Click.
- **Sell menu** — drop one stack into the GUI, type a price, done.
- **History, top and watchlist** — `/ah history`, `/ah top`, `/ah watch diamond` with live notifications.
- **Offline-friendly** — won items and seller payouts are stored as claims and delivered on join.
- **Russian and English** messages out of the box.

### For admins

- **Three storage backends** — YAML, SQLite, MySQL/MariaDB.
- **Tax groups and lot-limit groups** through permissions (`auction.group.vip`).
- **Server-wide lot cap**, amount-per-lot cap, blocked materials and price bounds.
- **Auto-buyout** of stale lots at a configurable discount.
- **Transaction log** for auditing.
- **Hot reload** with `/ah reload`.

## Commands

| Command | What it does |
|---|---|
| `/ah` | Open the auction house (aliases: `/auction`, `/аукцион`) |
| `/ah sell <price> [amount]` | List the item in your hand |
| `/ah auction <start> [hours] [buyout]` | Create a bid lot |
| `/ah sellmenu <price>` | Open the sell menu |
| `/ah buy <id> [amount]` | Buy a lot or part of a stack |
| `/ah bid <id> <amount>` | Place a bid |
| `/ah search <text>` | Search by item name or seller |
| `/ah history` | Purchase and sale history |
| `/ah top [buyers]` | Top sellers (or buyers) |
| `/ah watch <text>` / `list` / `remove` / `clear` | Manage the watchlist |
| `/ah my` | Your own lots |
| `/ah expired` | Expired lots and claims |
| `/ah claims` | Claim everything at once |
| `/ah admin <reload\|payouts\|autobuy\|info\|remove\|end> [id]` | Admin tools |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `auction.use` | everyone | Open the auction house |
| `auction.sell` | everyone | List items |
| `auction.buy` | everyone | Buy items and place bids |
| `auction.tax.bypass` | op | Sell without sales tax |
| `auction.bypass.blocked` | op | List blocked materials |
| `auction.limit.<n>` | — | Grant a lot limit of `n` |
| `auction.group.<name>` | — | Tax/limit group from `config.yml` |
| `auction.admin` | op | Admin commands and `/ah reload` |

## Installation

1. Drop `AurumAuction.jar` into `plugins/`.
2. Install **Vault** and an economy plugin (EssentialsX, CMI, …).
3. Start the server once, then edit `plugins/AurumAuction/config.yml`:
   - `storage.type: YAML` — single small server;
   - `storage.type: SQLITE` — single server with a large lot count;
   - `storage.type: MYSQL` + `multi-server.enabled: true` — a network.
4. Restart or run `/ah reload`.

## Compatibility

| Platform | Versions |
|---|---|
| Paper / Purpur / Spigot | 1.16.5 – 1.21.x and 26.x |
| Folia | 1.19.4+ (region schedulers) |
| Java | 8+ (built for Java 8, runs on 21/25) |
| Bedrock | Geyser + Floodgate (lore trimming) |
| Economy | Vault (optional soft-depend) |

Tested on Paper 1.16.5, 1.21.1, 26.1.2 and 26.3, plus Folia 26.1.2.

## Configuration highlights

```yaml
storage:
  type: MYSQL          # YAML / SQLITE / MYSQL
  migrate-yaml: true   # import lots.yml into an empty SQL database

lots:
  expire-hours: 48
  tax-percent: 5.0
  tax-groups:
    vip: 3.0
    premium: 1.0
  max-amount-per-lot: 64
  buy-confirm: true

bids:
  enabled: true
  min-increment-percent: 5.0
  extend-seconds: 60   # anti-snipe window
  default-hours: 24

multi-server:
  enabled: true
  server-id: 1         # unique per server, 0-1023
  sync-seconds: 5

bedrock:
  max-lore-lines: 8    # trim lore for Floodgate players
```

## Links

- Source: https://github.com/xuviga/AurumAuction
- Full guide: `docs/GUIDE.md`
- Issues and feature requests: GitHub Issues
