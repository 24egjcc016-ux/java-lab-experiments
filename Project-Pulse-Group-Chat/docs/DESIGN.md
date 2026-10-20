# Design notes

Pulse is a desktop chat client that someone might leave open all afternoon, so
the interface stays quiet. Structure comes from work chat tools: a channel
header, one message column, a member list, a composer. The surface treatment is
flat and dark, with luminance steps and hairlines instead of decoration.

Three rules run through the whole app:

1. Surfaces step up in brightness. A panel is not a different colour, it is a
   slightly lighter dark.
2. Colour means a person or a status. Nothing else is coloured.
3. There is one light element on screen: the primary button. When a screen has
   no primary action, nothing is bright.

## Tokens

Every colour is declared once on `.root` in
`src/main/resources/com/pulse/app.css` as a JavaFX looked-up colour, then
referenced by name.

| Token | Value | Used for |
|---|---|---|
| `-fx-bg` | `#0e0f11` | canvas behind the message list and pages |
| `-fx-bg-raised` | `#131417` | title bar, chat header, sidebar, composer |
| `-fx-panel` | `#17181c` | sign-in card, server panels |
| `-fx-surface` | `#1d1f23` | inputs, composer box |
| `-fx-code-bg` | `#101114` | server log area |
| `-fx-ink` | `#eceef0` | headings, names you own, stat values |
| `-fx-ink-body` | `#c6cbd1` | message text |
| `-fx-ink-muted` | `#8f959d` | secondary text, status words |
| `-fx-ink-subtle` | `#7a8089` | timestamps, captions, panel labels |
| `-fx-line` | `rgba(255,255,255,0.08)` | hairlines between regions |
| `-fx-line-strong` | `rgba(255,255,255,0.14)` | control borders |
| `-fx-primary-bg` | `#e8eaed` | the one light button |
| `-fx-primary-ink` | `#121316` | text on that button |
| `-fx-success` | `#4cb782` | connected dot |
| `-fx-danger` | `#e5484d` | offline dot, disconnect banner |

There is no accent colour. An earlier draft used an indigo accent for actions
and own messages; it made every screen look like a template. Removing it left
the light button as the only thing that pulls the eye, which is enough.

## People

Each name hashes to one of eight muted hues, defined as `.user-0` to `.user-7`.
The hue appears twice per person: as a 16% tint behind the initials in the
avatar circle, and as the colour of the initials and the name in the message
list. Nothing else in the row is coloured, so a busy conversation still reads
as one column of text.

The palette is deliberately desaturated (`#d9a05b`, `#6f9fd8`, `#7dbb8e`,
`#d98a94`, `#a795cf`, `#6fb5ad`, `#aab06a`, `#98a2ad`). Saturated avatars would
fight the message text for attention.

## Messages

Every message is a row in the same place: a 26px avatar, then the name and time
on one line, then the text at the full column width.

- A run of messages from one person keeps the name line once. Later rows drop
  the avatar and indent under the text above them.
- Your own messages use the same row shape and are labelled **You**. They are
  not right-aligned and have no bubble. Two people talking look like one
  transcript instead of two columns.
- A join or leave notice is a small centred line and breaks the run, so the
  next message starts a fresh group.
- Body text is capped at 620px. Long lines are hard to read; short ones look
  like a chat.

## Chrome

The window is undecorated, so the title bar is drawn by the app: the name, an
em dash, then the current screen or user, and window controls on the right.
Anything draggable is draggable by that bar. The bar is the same height and
colour on the sign-in screen, the chat window and the server.

Sign-in is one 360px card with three fields and a button. No logo tile, no
tagline, no footer. The server dashboard is a header, three flat numbers
separated by hairlines, and two panels: a log and the connected people. Neither
screen has a card for a single number or a pill for a single word.

## Type

Segoe UI throughout, because it is on every Windows machine this will be marked
on, with Consolas for the server log.

| Size | Use |
|---|---|
| 10px | timestamps, panel labels |
| 10.5–11px | hint text, system notices |
| 12–13px | fields, rows, message text |
| 13.5px | channel name, message author |
| 14–16px | screen titles, stat values |

Weight does most of the work: names, titles and stat values are bold, body text
is not. Only the sign-in card steps up to 15px, and nothing is 30px.

## Spacing and shape

- 4px base. Gaps are 2, 4, 6, 8, 10, 12, 14, 16.
- 6px radius on controls, 8px on panels and the composer box, 10px on the
  sign-in card. Avatars are circles.
- Borders are 1px hairlines, never heavier. The only filled row is the hover
  state at 2–3% white.
- The message list has no outer padding on the sides; rows carry 16px, so a
  hover highlight reaches the edge of the column.

## Motion

JavaFX CSS has no transitions, so hover and focus changes are instant. That is
correct for a chat client: the only thing that should move on its own is the
message list, which stays pinned to the newest line as rows arrive.

## Accessibility

- Body text `#c6cbd1` on `#0e0f11` is about 11.8:1.
- Muted text `#8f959d` is about 6.4:1, and the quietest ink `#7a8089` is about
  4.8:1, so timestamps and captions still pass AA at their small sizes.
- Status is never colour alone. The dot is always next to a word: "Connected",
  "Offline", or "Listening on 5000". The banner spells out what happened.
- Focus is a 1px light ring on the control, visible against every surface.
- Enter sends, Shift+Enter adds a line, and every control is reachable with
  Tab.
