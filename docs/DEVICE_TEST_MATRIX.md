# Device test matrix template

Record evidence per device; never infer one OEM's behavior from another.

| Device | Android | SoC | Camera ID/physical ID | Lens | Preview | RAW still | Continuous RAW | Max RAW | 60fps preview | Notes/quirks |
|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | |

For every candidate lens test:
- characteristics readable
- camera open succeeds repeatedly
- preview session creates
- timestamps advance
- non-empty frames arrive
- orientation/aspect correct
- RAW stream works if advertised
- RAW + preview combination works if required
- close/reopen does not wedge HAL
- background/foreground lifecycle recovers
- thermal and memory behavior remains stable
