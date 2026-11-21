---
name: VisionSpace
description: "The owner's archive: editorial serif names, mono ledger metadata, photographs as the only saturated color."
colors:
  dark-paper: "#05060c"
  dark-raised: "#0a0c15"
  dark-ink: "#f4f1ea"
  dark-ink-muted: "rgba(244, 241, 234, 0.74)"
  dark-ink-faint: "rgba(244, 241, 234, 0.52)"
  dark-rule: "rgba(244, 241, 234, 0.12)"
  dark-rule-strong: "rgba(244, 241, 234, 0.24)"
  dark-accent: "#b7a2ff"
  dark-positive: "#86e3c8"
  dark-danger: "#ff8fa3"
  light-paper: "#faf7ff"
  light-raised: "#f2edff"
  light-ink: "#1e1b4b"
  light-ink-muted: "rgba(30, 27, 75, 0.82)"
  light-ink-faint: "rgba(30, 27, 75, 0.68)"
  light-rule: "rgba(124, 58, 237, 0.16)"
  light-rule-strong: "rgba(124, 58, 237, 0.32)"
  light-accent: "#7c3aed"
  light-positive: "#047857"
  light-danger: "#b91c1c"
  accent-ink: "#0b0716"
  pass-violet-deep: "#120a26"
  pass-violet-mid: "#231044"
  pass-violet-bright: "#3b1d70"
  plate-caption-dark: "rgba(5, 6, 12, 0.86)"
  plate-caption-light: "rgba(30, 27, 75, 0.9)"
typography:
  display:
    fontFamily: "'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif"
    fontSize: "clamp(52px, 8vw, 116px)"
    fontWeight: 600
    lineHeight: 0.98
    letterSpacing: "0.01em"
  headline:
    fontFamily: "'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif"
    fontSize: "clamp(38px, 5vw, 62px)"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "0.02em"
  title:
    fontFamily: "'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif"
    fontSize: "clamp(30px, 3.4vw, 46px)"
    fontWeight: 600
    lineHeight: 1.16
    letterSpacing: "0.01em"
  measurement:
    fontFamily: "'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif"
    fontSize: "clamp(26px, 3vw, 40px)"
    fontWeight: 600
    lineHeight: 1
    fontFeature: "tnum"
  body:
    fontFamily: "'Noto Sans SC', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.85
  label:
    fontFamily: "'JetBrains Mono', 'SFMono-Regular', 'Consolas', monospace"
    fontSize: "10px"
    fontWeight: 400
    lineHeight: 1.3
    letterSpacing: "0.18em"
rounded:
  sharp: "0px"
  control: "2px"
  pill: "999px"
spacing:
  space-2: "8px"
  space-3: "12px"
  space-4: "16px"
  space-5: "20px"
  space-6: "24px"
  space-8: "32px"
  space-12: "48px"
components:
  button-solid:
    backgroundColor: "{colors.dark-ink}"
    textColor: "{colors.dark-paper}"
    rounded: "{rounded.control}"
    padding: "0 18px"
    height: "42px"
  button-solid-hover:
    backgroundColor: "{colors.dark-accent}"
    textColor: "{colors.accent-ink}"
  button-line:
    backgroundColor: "transparent"
    textColor: "{colors.dark-ink}"
    rounded: "{rounded.control}"
    padding: "0 18px"
    height: "42px"
  button-quiet:
    backgroundColor: "transparent"
    textColor: "{colors.dark-ink-muted}"
    padding: "0"
  button-danger:
    backgroundColor: "transparent"
    textColor: "{colors.dark-danger}"
    rounded: "{rounded.control}"
    padding: "0 18px"
    height: "42px"
  input-field:
    backgroundColor: "transparent"
    textColor: "{colors.dark-ink}"
    rounded: "{rounded.control}"
    padding: "11px 14px"
    height: "46px"
  chip:
    backgroundColor: "transparent"
    textColor: "{colors.dark-ink-muted}"
    rounded: "{rounded.pill}"
    padding: "4px 10px"
  plate:
    backgroundColor: "{colors.dark-raised}"
    textColor: "{colors.dark-ink}"
    rounded: "{rounded.sharp}"
  member-pass:
    backgroundColor: "{colors.pass-violet-mid}"
    textColor: "#ffffff"
    rounded: "{rounded.sharp}"
    padding: "34px"
---

# Design System: VisionSpace

## Overview

**Creative North Star: "The Private Print Room"**

The user center is an owner's archive, not a settings dashboard. It is lit like a print room after hours: near-black paper (#05060c), warm ink (#f4f1ea), one violet (#b7a2ff) for index numbers, selection and focus, and the user's own photographs as the main source of color on the page. There is no glass, no gradient headline and no shadowed card. Structure comes from hairlines, tonal paper steps, a ruled ledger and a numbered index rail whose marker travels between five records.

Type does the editorial work. The owner's name is set at billboard scale in Noto Serif SC; record titles, the member pass and measurement numerals stay serif; the interface speaks in Noto Sans SC; catalog metadata, dates, counts, IDs and states are JetBrains Mono. A staggered six-plate contact sheet prints recent uploads beside the masthead, each plate an unrounded frame whose caption slides in on hover or keyboard focus.

Light and dark are the same layout under two lights: paper (#faf7ff), ink (#1e1b4b), violet (#7c3aed), with the same rhythm and rules. All photography is user content; the capture set under `.impeccable/review` uses picsum placeholders and is not a shipping asset. The older global Fredoka/Nunito tokens and glass helpers in `design-tokens*.css` belong to surfaces outside this system.

**Key Characteristics:**
- Near-black archive paper with warm ink; a warm-lilac paper and indigo ink in the light theme.
- Noto Serif SC at billboard scale for the name, headings and measurements.
- JetBrains Mono for catalog metadata, measurements, dates and state tokens.
- Hairline rules and tonal paper steps instead of cards, shadows or glass.
- User photographs supply the imagery and the dominant color; chrome stays paper, ink and violet.
- A 01-05 index rail with a traveling 2px marker, beside a staggered contact sheet.

## Colors

A restrained archival palette: two papers, two inks, one violet lamp, and two status colors. Every theme has the same roles; only the values change.

### Primary
- **Archive Violet** (#b7a2ff dark / #7c3aed light): selection, focus rings, the active index marker, active radio-card borders and the record index numeral. It is the only accent hue; its scarcity is the point.

### Secondary
- **Pass Violet Deep** (#120a26), **Pass Violet Mid** (#231044), **Pass Violet Bright** (#3b1d70): the three-stop gradient of an active member pass, plus a radial highlight of rgba(183, 162, 255, 0.24). This is the one filled color object in the system and it stays inside the violet family.

### Tertiary
- **Mint Verdict** (#86e3c8 dark / #047857 light): positive status, VIP badge and earned-perk checks.
- **Rose Alarm** (#ff8fa3 dark / #b91c1c light): destructive actions, redeem failures and form errors. Destructive borders are `color-mix(in srgb, var(--uc-danger) 45%, transparent)`; hover fills use 8% of the same color.

### Neutral
- **Archive Paper** (#05060c dark / #faf7ff light): the page ground and the color that punches through the member-pass perforations.
- **Raised Paper** (#0a0c15 dark / #f2edff light): plate and archive-frame grounds, portrait ground and the unfilled pass.
- **Warm Ink** (#f4f1ea dark / #1e1b4b light): names, headings and primary values.
- **Muted Ink** (rgba(244, 241, 234, 0.74) dark / rgba(30, 27, 75, 0.82) light): body prose, quiet buttons and secondary values. The light alpha is the post-fix value that keeps prose above 4.5:1.
- **Faint Ink** (rgba(244, 241, 234, 0.52) dark / rgba(30, 27, 75, 0.68) light): mono labels, hints, placeholders and disabled metadata. The light alpha is also the post-fix value.
- **Hairline Rule** (rgba(244, 241, 234, 0.12) dark / rgba(124, 58, 237, 0.16) light): every divider, record boundary and ledger line.
- **Strong Rule** (rgba(244, 241, 234, 0.24) dark / rgba(124, 58, 237, 0.32) light): control borders, dashed pass perforation and the meta separator lines.
- **Accent Ink** (#0b0716): text and switch thumbs placed on Archive Violet in the dark theme.

### Named Rules
**The Photographs Carry the Color Rule.** Chrome stays inside paper, ink and violet. Outside that violet, saturation enters only through the user's uploaded images; no decorative gradients, neon glows or gradient text belong to the archive.

**The Two Lightings Rule.** Dark and light are one layout under two lights. Only the page-local color variables swap; type, spacing, shape and motion do not fork by theme.

**The One Deep-Violet Object Rule.** The active member pass is the only filled gradient surface. It is the single deliberate exception to flat paper, and it never leaves the violet family.

## Typography

**Display Font:** Noto Serif SC (with Songti SC, STSong, SimSun, serif)
**Body Font:** Noto Sans SC (with PingFang SC, Hiragino Sans GB, Microsoft YaHei, sans-serif)
**Label/Mono Font:** JetBrains Mono (with SFMono-Regular, Consolas, monospace)

**Character:** A bilingual editorial pairing: the serif gives names and records the weight of a printed catalogue, the sans keeps tasks plain and legible, and the mono turns measurements and identifiers into catalog data. Chinese is the primary voice; Latin metadata is the second voice.

### Hierarchy
- **Display** (600, clamp(52px, 8vw, 116px), 0.98): the owner's name in the masthead. At 640px and below it becomes clamp(44px, 13vw, 64px).
- **Headline** (600, clamp(38px, 5vw, 62px), 1): the member pass status (VIP / 普通用户).
- **Title** (600, clamp(30px, 3.4vw, 46px), 1.16): each record heading. Empty-state titles use the same serif at clamp(22px, 2.4vw, 30px).
- **Measurement** (600, clamp(26px, 3vw, 40px), 1, tabular numerals): the three ledger values (uploads, likes, pass rate). Ledger labels above them are 9px mono at 0.16em.
- **Body** (400, 15-17px, 1.85-1.9): bio, ledes and prose. The bio caps at 46ch, record ledes at 60ch, profile prose at 56ch; fields use 15px at 1.6.
- **Label** (400, 8-11px, 0.14-0.2em, uppercase): catalog metadata, dates, counts, units, IDs, status tokens and the record category. Mono never carries body copy or prose.

### Named Rules
**The Serif Names Rule.** Names, record titles, the pass and measurement numerals are set in Noto Serif SC. Never set UI chrome, labels or long prose in the serif.

**The Ledger Mono Rule.** Any measurement, date, archive ID, count, unit or state token is JetBrains Mono, and numbers use tabular figures. Mono is catalog data, not decoration.

## Layout

The shell is a single centered column: `max-width: 1360px`, `margin: 0 auto`, `padding: 0 clamp(20px, 5vw, 64px)`, with `overflow-x: clip` on the page. The route is `fullWidth` and carries no `showFooter` meta, so the global header stays above it, the global footer is not rendered, and the page supplies its own archive footer.

The masthead is a two-column grid, `minmax(0, 1fr) minmax(300px, 42%)`, `gap: clamp(32px, 6vw, 88px)`, `align-items: end`, with `padding: clamp(44px, 8vh, 96px) 0 clamp(40px, 6vh, 72px)` and a 1px bottom rule. The left column carries the meta line (archive ID, member-since, role, separated by 28x1px rules), the clamped display name, a bio capped at 46ch, the optional VIP line and a three-column ledger bounded by hairlines. The ledger's `dd` values are serif, tabular and clamped 26-40px. The right column is the contact sheet.

Below, the body is `grid-template-columns: 216px minmax(0, 1fr)`, `gap: clamp(32px, 5vw, 76px)`, `padding: clamp(32px, 5vh, 56px) 0 clamp(48px, 8vh, 96px)`. The 216px index rail is sticky at `top: calc(72px + 28px)` (global header plus 28px breathing room); it collapses to 188px at 1080px. At 860px the body stacks to one column and the rail becomes a horizontal tab strip sticky at `top: 72px`, full-bleed through negative shell margins, with 56px edge fades that appear only when content is clipped, 48px tab height, hidden head/foot/hints/marker and a 2px accent underline on the selected tab.

The record template is consistent across all five sections: a header with an 11px mono index in the accent color, a serif h2, and a right-aligned 10px mono category label, closed by a 1px rule; an optional lede at 16px/1.9 capped at 60ch; then the record body with `padding-top: clamp(24px, 4vh, 40px)`. Section swaps use a short clipped reveal (`clip-path` from `inset(0 0 100% 0)` to `inset(0)` plus a 14px rise), never a full entrance sequence.

Spacing uses the global 4px-base steps actually reused here: 8, 12, 16, 20, 24, 32 and 48px. Fluid rhythm is handled by clamp() for section padding and grid gaps rather than by adding new scale values.

Responsive breakpoints:
- **1080px**: masthead narrows to `minmax(260px, 40%)`; rail to 188px; rail hints hide.
- **900px**: profile grid, member grid and archive grid drop to fewer columns or one.
- **860px**: masthead stacks; body stacks; rail becomes the horizontal sticky scroller.
- **640px**: masthead top padding drops to 32px; name becomes clamp(44px, 13vw, 64px); plates drop to two columns; footer stacks; the member pass becomes one column; feedback type cards stack.
- **520px**: editable-field rows stack.
- **320px**: supported floor; no horizontal page overflow.

## Elevation & Depth

The system is flat. There is not a single `box-shadow` in the user-center surface. Depth is conveyed by tonal paper steps (Archive Paper to Raised Paper), 1px hairlines, and a static atmosphere layer that never receives pointer events: a radial ground gradient, a 96px grid at rgba(244, 241, 234, 0.026) masked by a radial ellipse (72% x 60% at 50% 24%), and two blurred color washes at `filter: blur(110px)`. Wash A is the violet glow (rgba(111, 75, 216, 0.26) dark, rgba(168, 85, 247, 0.12) light); Wash B is a faint mint/cyan counter-glow (rgba(134, 227, 200, 0.08) dark, rgba(34, 211, 238, 0.07) light). The light theme swaps the ground to white-to-paper and the grid to violet at 0.06.

Overlays sit on tone, not shadow: plate captions use rgba(5, 6, 12, 0.86) dark / rgba(30, 27, 75, 0.9) light with a 1px white-at-0.14 top rule. The sticky mobile rail uses the solid page paper and z-index 6, still without a shadow. Focus is an outline, never a glow.

### Named Rules
**The Flat Paper Rule.** No box-shadows, glass panels or blurred card backdrops. Separate surfaces with a hairline or a tonal paper step; reserve blur for the single static atmosphere layer.

## Shapes

Form language is printed matter: nearly square controls, square media, pill only where a physical token would be round. Buttons and fields take a 2px radius. Plates, archive frames, the portrait frame, the member pass, radio cards and empty states take 0. Chips, the switch track and its thumb take 999px. Every border is 1px.

Media ratios are fixed: 3/4 for contact-sheet plates, 4/5 for archive frames and the portrait, 92x92 for feedback upload tiles. The member pass is a landscape ticket with a 168px stub, a 1px dashed perforation and 16px punched circles cut from the paper color. Empty and inert areas use a 45-degree repeating hatch with a 1px line every 11-16px at 0.035-0.07 alpha. The pass guilloche is 11 hand-laid cubic curves at 0.8 stroke, not a raster texture.

### Named Rules
**The Nearly-Square Rule.** Controls get 2px, media and panels get 0, pills get 999px. There are no rounded cards in the archive.

## Components

### Buttons
- **Shape:** 2px radius, `min-height: 42px` (44px at 860px and below), `padding: 0 18px`, 13px/1 sans at weight 500, 9px gap, 150ms color/background/border transitions.
- **Solid:** Archive Ink fill with Archive Paper text. Hover swaps to Archive Violet with Accent Ink text; in the light theme the hover text is #fff.
- **Line:** transparent fill, Strong Rule border, Warm Ink text. Hover shifts border and text to Archive Violet.
- **Quiet:** no border, no padding, Muted Ink text with a 1px underline at 4px offset. Hover goes to Warm Ink.
- **Danger:** Rose Alarm text with a 45% mixed border; hover fills 8% Rose Alarm.
- **Hover / Focus:** icons nudge (1px, -1px) on solid/line and 2px right on quiet over 240ms; disabled state is opacity 0.42 with `not-allowed`; focus-visible is a 2px Archive Violet outline at 3px offset.

### Fields
- **Style:** `min-height: 46px`, `padding: 11px 14px`, 1px Strong Rule border, 2px radius, transparent fill, 15px/1.6 sans, accent caret.
- **Focus:** hover lifts the border to Faint Ink; focus shifts it to Archive Violet; focus-visible adds a 2px accent outline at 1px offset.
- **Labels:** 10px mono at 0.16em, uppercase, Faint Ink, with a flex space-between slot for a count or unit. Hints are 12px/1.7 in Faint Ink.
- **Ant Design tags:** the `.uc-tags` select is flattened to the same 46px transparent, 2px-radius field; selected values become 999px chips with a 1px Strong Rule border. Popups keep the global theme.

### Chips
- **Style:** 999px pill, transparent fill, 1px Strong Rule border, Muted Ink text at 12px/1.5, 4px x 10px padding, 6px gap.
- **State:** static learned-preference tokens; no hover or selected variant.

### Switch
- **Style:** 46x26 track, 999px radius, 1px Strong Rule border, transparent fill; 18px thumb at 3px inset in Faint Ink.
- **On:** track becomes Archive Violet, thumb becomes Accent Ink and translates 20px, and the 9px mono state label becomes Archive Violet. Disabled is opacity 0.5 with `wait`.

### Radio cards
- **Style:** three equal columns, 10px gap; square cards with 1px Strong Rule, 18px/16px/16px padding; 9px mono index, 22px Lucide icon at 1.5 stroke, 14px/500 name.
- **State:** hover lifts the border to Faint Ink; active uses an Archive Violet border and a 6% accent fill, with index, icon and a 15px check all turning accent. At 640px the cards become full-width horizontal rows with the index first.

### Index rail
- **Structure:** a 216px sticky `nav` containing a mono `INDEX` head, a `role="tablist"` with five `role="tab"` buttons and a 2px Archive Violet marker; a mono `VS / <archive-id>` foot.
- **State:** 56px rows, baseline-aligned 10px mono number, 14px label and an 8px mono hint that fades and slides in on hover or selection. The selected row is 600 weight with the number in accent; the marker translates `activeIndex * 56px` over 280ms.
- **Keyboard:** roving tabindex (selected tab 0, others -1), Arrow Up/Down and Left/Right both cycle, Home/End jump; `aria-orientation` switches to horizontal at 860px, where the list scrolls horizontally, scrolls the chosen tab to center and replaces the marker with a 2px bottom underline.

### Contact sheet
- **Style:** a head with a mono label and a two-digit count over a 1px rule; three columns at 10px gap, every even plate offset 18px; each plate is a square 3/4 button on Raised Paper with a 1px rule.
- **Image treatment:** `object-fit: cover` with `saturate(0.86) contrast(1.03)`; hover or focus-visible scales the image 1.035 and releases saturation to 1 over 600ms. A 9px white plate number sits top-left with a text shadow.
- **Caption:** a bottom overlay at rgba(5, 6, 12, 0.86) dark / rgba(30, 27, 75, 0.9) light, translated fully below the frame at rest and sliding to 0 over 320ms; 12px name plus a 9px mono `VIEW`.
- **Empty:** a hatched plate with a 9px mono slot number; loading dims plates to 0.55; the zero-work state offers a quiet "上传第一张" action.

### Archive grid
- **Style:** three columns at clamp(14px, 2.4vw, 28px) gap, two below 900px; each item is a 4/5 frame on Raised Paper with a 1px rule, the same image filter as the contact sheet, and an `OPEN →` mono reveal at bottom-right on hover/focus.
- **Caption:** below the frame, 13px Muted Ink name truncated with ellipsis and a 9px mono index.
- **Empty:** a square hatch panel with a 56px serif Ø (40px at 640px), a serif title, a hint and a solid upload button.

### Member pass
- **Default:** a 1fr / 168px grid, 224px minimum height, 1px Strong Rule border, Raised Paper fill; 11 concentric guilloche curves at 0.14 accent opacity; 9px mono issuer line; serif status; 14px holder; a hairline meta row with mono labels and 14px tabular values; a dashed left stub with a vertical mono number.
- **VIP:** the one filled object. Border transparent; a radial highlight at rgba(183, 162, 255, 0.24) over a 118-degree gradient from #120a26 through #231044 to #3b1d70; all text white; guilloche white at 0.55 opacity and 0.28; stub at rgba(0, 0, 0, 0.16).
- **Small screens:** at 640px the pass becomes one column and the stub turns horizontal with a dashed top edge and horizontal number.
- **Redeem:** a mono 14px uppercase code field at 46px next to a solid button; the result is a 13px inline status, mint for success and rose for error, announced politely.

### Status, errors and empty states
- Inline status rows are 13px/1.6 with a 7px icon gap; `--ok` uses Mint Verdict, `--error` uses Rose Alarm.
- Archive load failure is a `role="status"` line with a quiet retry; sheet loading is a mono `role="status"` line; form validation is a `role="alert"` line in Rose Alarm; redeem results use `role="status"` with `aria-live="polite"`.
- Save, upload and toast feedback uses Ant Design messages; the archive's own failures stay inline.
- Empty states never use stock photography. They use hatch, a serif mark or title, a hint and one action.

### Keyboard and announcement
- A skip link ("跳到主要内容") hides above the viewport and appears at 12px on focus, with Archive Ink fill and Archive Paper text.
- The rail implements tablist/tab/tabpanel semantics with `aria-controls`, a labelled panel, orientation switching and roving tabindex. The panel is focusable (`tabindex="0"`) so keyboard users can scroll it.
- Global focus-visible is a 2px Archive Violet outline at 3px offset; fields use a 1px offset; the hidden radio input draws its ring on the visible index span.
- Prose contrast is at least 4.5:1 in both themes, including the light-theme alphas 0.82 and 0.68. Primary controls are 44px tall at 860px and below, rail tabs are 48px, and the page is usable at 320px.
- `prefers-reduced-motion: reduce` collapses every animation and transition in the surface to 0.01ms and sets scroll behavior to auto.

## Do's and Don'ts

### Do:
- **Do** keep the shell at 1360px with clamp(20px, 5vw, 64px) gutters and a single centered column.
- **Do** set the owner's name in Noto Serif SC 600 at clamp(52px, 8vw, 116px) with 0.98 line-height.
- **Do** build structure from 1px rules and the Archive Paper to Raised Paper tonal step.
- **Do** render uploaded images at `saturate(0.86) contrast(1.03)` and release to full saturation on hover or focus.
- **Do** set measurements, dates, counts, units and IDs in JetBrains Mono with tabular numerals.
- **Do** keep both themes on one layout and swap only the page-local color variables.
- **Do** use 2px radius on controls, 0 on media, and 999px only for chips and the switch.
- **Do** show a 2px Archive Violet focus-visible outline at 3px offset on every interactive element.
- **Do** keep primary controls at 42px desktop and 44px at 860px and below.

### Don't:
- **Don't** add box-shadows, glass panels, backdrop blur or neon glows to the archive; the atmosphere layer is the only blur.
- **Don't** use gradient text or a second accent hue.
- **Don't** fill empty plates or empty states with stock imagery; use the hatch, the serif mark and one action.
- **Don't** use icons in place of photographs.
- **Don't** round media frames, cards or the member pass.
- **Don't** set names, record titles or measurements in the sans or the mono.
- **Don't** use the mono for prose or standalone decoration; it carries catalog metadata only.
- **Don't** introduce a third theme or per-section palettes.
