# Cơ chế gõ Tiếng Việt (Telex) trong HeliBoard Telex

Tài liệu này giải thích lớp Tiếng Việt được gắn vào HeliBoard như thế nào, từ lúc bàn phím khởi động
cho tới lúc một từ được ghi vào ô nhập, cùng cách giữ bản fork theo kịp HeliBoard gốc.
Các sơ đồ dùng [Mermaid](https://mermaid.js.org/) — GitHub hiển thị trực tiếp.

Bộ gõ Telex trong `viettelex-core/` và các tùy chọn gõ được chuyển thể từ
[VietTelex](https://github.com/ptrinh/viettelex) của Phil Trinh (giấy phép MIT, xem `viettelex-core/LICENSE`).

Mục lục:
1. [Ý tưởng chính](#1-ý-tưởng-chính)
2. [Ba tầng code](#2-ba-tầng-code)
3. [Khởi động và chọn bộ ghép chữ](#3-khởi-động-và-chọn-bộ-ghép-chữ)
4. [Gõ một phím](#4-gõ-một-phím)
5. [Thanh gợi ý](#5-thanh-gợi-ý)
6. [Kết thúc từ: tự khôi phục và ngữ cảnh tiếng Anh](#6-kết-thúc-từ-tự-khôi-phục-và-ngữ-cảnh-tiếng-anh)
7. [Sửa dấu từ đã gõ](#7-sửa-dấu-từ-đã-gõ)
8. [Cài đặt](#8-cài-đặt)
9. [Bảy tùy chọn nằm ở đâu trong code](#9-bảy-tùy-chọn-nằm-ở-đâu-trong-code)
10. [Kiểm thử](#10-kiểm-thử)
11. [Theo kịp HeliBoard gốc](#11-theo-kịp-heliboard-gốc)

---

## 1. Ý tưởng chính

HeliBoard (kế thừa từ bàn phím AOSP) đã có sẵn khái niệm **combiner** — bộ ghép chữ nằm giữa phím bấm
và chữ hiển thị, đang dùng cho tiếng Hàn (`HangulCombiner`) và tiếng Bengal (`BnKhiproCombiner`).
Tiếng Việt Telex được thêm vào **đúng theo cơ chế đó**: một combiner mới tên `viet_telex`.

Mọi phần "hiểu tiếng Việt" (ghép dấu, kiểm tra âm tiết, nhận diện tiếng Anh, gợi ý) nằm trong một
module Kotlin thuần không phụ thuộc Android. HeliBoard chỉ biết gọi vài **hook** một dòng.

> HeliBoard vẫn lo phần bàn phím: giao diện, từ điển, ghi chữ vào ô nhập, con trỏ.
> Lớp Tiếng Việt chỉ chen vào ở 4 thời điểm — **khởi động, mỗi phím, thanh gợi ý, kết thúc từ** —
> cộng với các đường **sửa dấu từ đã gõ**.

## 2. Ba tầng code

```mermaid
flowchart TB
    subgraph UP["HeliBoard gốc — chỉ thêm lời gọi hook"]
        LatinIME
        InputLogic
        WordComposer
        Suggest
        CombinerChain
        SettingsUI["SubtypeScreen / SettingsContainer"]
    end
    subgraph GLUE["Lớp keo — app/.../helium314/keyboard/viettelex/"]
        Hooks["VietTelexHooks<br/>mọi logic chen vào luồng gõ"]
        Combiner["VietTelexCombiner<br/>Combiner của HeliBoard"]
        VSettings["VietTelexSettings<br/>7 tùy chọn vn_*"]
        VUI["VietnameseSettings<br/>giao diện cài đặt"]
    end
    subgraph CORE["Module :viettelex-core — Kotlin thuần"]
        Engine["TelexEngine<br/>ghép dấu, kiểm tra âm tiết,<br/>tiếng Anh, sửa ngược"]
        VNSuggest["VNSuggest + vnlexicon.bin<br/>gợi ý từ"]
        Fixer["AdjacentKeyFixer<br/>sửa chạm trượt"]
        ReEdit["ReEdit<br/>quy tắc sửa dấu"]
    end
    LatinIME --> Hooks
    InputLogic --> Hooks
    Suggest --> Hooks
    WordComposer --> Combiner
    CombinerChain --> Combiner
    SettingsUI --> VUI
    Hooks --> Combiner
    Hooks --> VNSuggest
    Hooks --> Fixer
    Hooks --> ReEdit
    Combiner --> Engine
    Combiner --> VSettings
    Hooks --> VSettings
    VUI --> VSettings
```

| Tầng | Vị trí | Sửa file của HeliBoard? |
|---|---|---|
| Engine, từ vựng, sửa chạm trượt | `viettelex-core/` | Không |
| Lớp keo | `app/src/main/java/helium314/keyboard/viettelex/` | Không (file riêng) |
| Chuỗi giao diện | `res/values*/strings_viettelex.xml` | Không (file riêng) |
| Lời gọi hook | `InputLogic`, `WordComposer`, `Suggest`, `CombinerChain`, `LatinIME`, … | Có, vài dòng |

Danh sách đầy đủ từng dòng hook: [`UPSTREAM_SYNC.md`](UPSTREAM_SYNC.md#hook-points-in-upstream-files).

## 3. Khởi động và chọn bộ ghép chữ

```mermaid
sequenceDiagram
    participant OS as Android
    participant IME as LatinIME
    participant H as VietTelexHooks
    participant S as VietTelexSettings
    participant IL as InputLogic
    participant WC as WordComposer
    participant CC as CombinerChain

    OS->>IME: onCreate()
    IME->>H: onCreate(context)
    H->>H: KeyboardData.install(mở assets/vnlexicon.bin)
    H->>S: install() — đọc 7 khóa vn_*, đăng ký lắng nghe thay đổi
    OS->>IME: onStartInputView (subtype vi)
    IME->>IL: startInput(combiningSpec của subtype)
    IL->>H: combiningSpecFor(spec, settings)
    H-->>IL: "viet_telex" (subtype vi không ghi rõ thì cũng dùng Telex)
    IL->>WC: restartCombining("viet_telex")
    WC->>CC: new CombinerChain("viet_telex")
    CC->>CC: thêm VietTelexCombiner
    IL->>H: resetContext() — xóa ngữ cảnh tiếng Anh của phiên trước
```

Subtype tiếng Việt khai báo `CombiningRules=viet_telex` trong `res/xml/method.xml`;
`SubtypeLocaleUtils.getCombiningRulesExtraValue` và `VietTelexHooks.combiningSpecFor` bảo đảm subtype
`vi` do người dùng tự thêm cũng được dùng Telex.

## 4. Gõ một phím

Ví dụ gõ `t i e e n g s`:

```mermaid
sequenceDiagram
    participant K as Phím
    participant IL as InputLogic
    participant H as VietTelexHooks
    participant WC as WordComposer
    participant C as VietTelexCombiner
    participant E as TelexEngine
    participant TF as Ô nhập

    K->>IL: onCodeInput('s')
    IL->>H: beforeCodeInput(...) — trường hợp đặc biệt, xem mục 7
    H-->>IL: ACTION_NONE
    IL->>WC: processEvent('s')
    WC->>C: processEvent('s')
    C->>C: syncSettings() — chép tùy chọn vào engine
    C->>E: feed('s')
    E-->>C: composed = "tiếng"
    C-->>WC: event "đã tiêu thụ" + combiningStateFeedback "tiếng"
    WC->>WC: typedWord = "tiếng", rawKeystrokes = "tieengs"
    IL->>TF: setComposingText("tiếng") — chữ gạch chân
```

Điểm mấu chốt:

- Combiner **nuốt** phím chữ cái: phím không đi thẳng ra màn hình mà vào engine; HeliBoard hiển thị
  chữ engine trả về như một từ đang soạn (composing).
- `WordComposer` giữ thêm **chuỗi phím gốc** (`tieengs`) — gợi ý và sửa chạm trượt cần nó.
- Ký tự không phải chữ cái (số, dấu nối) khiến engine chốt phần đang ghép rồi nối ký tự vào sau.
- Ô mật khẩu và ô kiểu "null" không đi qua engine.

## 5. Thanh gợi ý

```mermaid
flowchart LR
    A["Suggest<br/>getSuggestedWords"] --> B["Gợi ý từ từ điển<br/>main_vi.dict (HeliBoard)"]
    B --> C["VietTelexHooks.addSuggestions"]
    C --> D{"VNSuggest:<br/>có từ bắt đầu bằng<br/>chữ đang gõ?"}
    D -- có --> E["Thêm tối đa 5 từ hoàn chỉnh<br/>việ → việt, việc…"]
    D -- không --> F["AdjacentKeyFixer.lexiconCorrection<br/>đổi phím kề / đảo 2 phím<br/>cyar → của, bnaj → bạn"]
    C --> G["AdjacentKeyFixer.lexiconToneSlip<br/>khi chưa có bản sửa ở trên"]
    E --> H["Thanh gợi ý"]
    F --> H
    G --> H
```

Sửa chạm trượt chỉ **đề xuất** trên thanh gợi ý, không tự thay chữ. Tắt "Gợi ý sửa lỗi chạm trượt"
thì bỏ hai nhánh `AdjacentKeyFixer`.

## 6. Kết thúc từ: tự khôi phục và ngữ cảnh tiếng Anh

```mermaid
sequenceDiagram
    participant K as Dấu cách
    participant IL as InputLogic
    participant H as VietTelexHooks
    participant C as VietTelexCombiner
    participant E as TelexEngine
    participant TF as Ô nhập

    K->>IL: handleSeparatorEvent(' ')
    IL->>H: finishBoundary(wordComposer)
    H->>C: commitBoundary()
    C->>E: commitText(autoRestore)
    Note over E: "tẽt" không phải âm tiết hợp lệ / là từ tiếng Anh<br/>→ trả lại phím gốc "text"
    E-->>C: "text"
    H->>H: cập nhật typedWord, xóa autocorrect đang chờ
    IL->>TF: commit "text" + " " (đường commit bình thường của HeliBoard)
    IL->>H: noteBoundaryCharacter(' ')
    H->>C: noteBoundaryCharacter(' ', connection)
    C->>E: noteBoundaryCharacter(' ')
    alt Engine vừa chắc chắn câu là tiếng Anh
        E-->>C: pendingRetroCorrection ("hí cả í " → "his car is ")
        C->>TF: xóa đoạn cũ, ghi đoạn đã sửa
    end
```

- **Tự khôi phục từ Tiếng Anh**: lúc chốt từ, nếu kết quả không phải âm tiết Việt hợp lệ hoặc là từ
  tiếng Anh đã biết, engine trả lại đúng phím gốc (`text`, `google`, `user`).
- **Quyết định theo ngữ cảnh**: engine nhớ vài từ gần nhất. Khi một "mạch" tiếng Anh trở nên chắc chắn
  (`very`, `the`…), nó sửa ngược các từ trước đã bị bỏ dấu nhầm. Chỉ chạy khi bật cả tùy chọn này
  lẫn "Tự khôi phục"; dấu chấm, xuống dòng hay di chuyển con trỏ sẽ xóa ngữ cảnh.

## 7. Sửa dấu từ đã gõ

Có ba đường đưa một từ đã xong trở lại engine:

```mermaid
flowchart TD
    A["⌫ ngay sau dấu cách"] --> R1["HeliBoard mở lại từ dưới con trỏ<br/>WordComposer.setComposingWord"]
    B["Đưa con trỏ về cuối một từ"] --> X["InputLogic.resetEntireInputState<br/>→ VietTelexHooks.resetContext"] --> R1
    C["Gõ phím dấu ngay sau một từ<br/>khi không đang soạn từ"] --> P["VietTelexHooks.beforeCodeInput<br/>xóa từ khỏi ô nhập, nạp vào engine"]
    R1 --> RW["VietTelexCombiner.resumeWord(word)"]
    RW --> Q{"Engine còn bản chụp<br/>của từ vừa chốt?"}
    Q -- "có (đường ⌫)" --> O["reopenLastCommit()<br/>khôi phục đủ phím gốc"]
    Q -- không --> PS["Đánh dấu 'chờ nạp lại'<br/>(pendingSeedWord)"]
    PS --> N["Phím kế tiếp quyết định<br/>(sơ đồ bên dưới)"]
```

Khi từ đang "chờ nạp lại", phím kế tiếp được xử lý như sau:

```mermaid
flowchart TD
    K["Phím chữ cái ch"] --> S{"seed(word) được?<br/>gõ ngược ra phím gốc,<br/>gõ lại phải ra y chữ cũ"}
    S -- được --> F["engine.feed(ch)"]
    F --> T{"ch là phím dấu hoặc a/e/o/d<br/>và chữ đã biến đổi?"}
    T -- có --> OK1["Giữ: học + j… / tô"]
    T -- không --> AP{"Chỉ nối thêm chữ?<br/>composed == word + ch"}
    AP -- có --> OK2["Giữ từ trong engine<br/>tiê + n → tiên, gõ tiếp g s → tiếng"]
    AP -- không --> LIT["Bỏ, gõ như chữ thường"]
    S -- không --> RP{"Từ toàn ASCII<br/>và ch là phím dấu/a/e/o/d?"}
    RP -- có --> RE["Gõ lại phím gốc + ch<br/>vieet + j"]
    RE --> V{"Kết quả là âm tiết Việt<br/>mà lúc chốt không bị khôi phục?"}
    V -- có --> OK3["Giữ: việt"]
    V -- không --> LIT2["Bỏ, gõ như chữ thường<br/>google + s → googles"]
    RP -- không --> LIT
```

Tắt "Sửa dấu từ đã gõ" thì từ được mở lại ở trạng thái **khóa**: mọi chữ gõ thêm chỉ được nối vào,
không ghép dấu.

## 8. Cài đặt

```mermaid
flowchart LR
    UI["Cài đặt → Ngôn ngữ → Tiếng Việt<br/>VietnameseSubtypeSettings"] -->|ghi| P[("SharedPreferences<br/>vn_free_marking, vn_simple_telex, …")]
    P -->|OnSharedPreferenceChangeListener| VS["VietTelexSettings.current"]
    VS -->|mỗi phím: syncSettings| ENG["TelexEngine"]
    VS --> HK["VietTelexHooks<br/>(sửa dấu, sửa chạm trượt)"]
```

- Bảy khóa `vn_*` được đọc trực tiếp; `Settings`, `SettingsValues`, `Defaults` của HeliBoard không đổi.
- Thay đổi có hiệu lực ngay nhờ bộ lắng nghe.
- `VietTelexSettings.current` bằng `null` khi chưa cài (trong unit test thuần JVM); code coi `null`
  là "dùng hành vi mặc định".

## 9. Bảy tùy chọn nằm ở đâu trong code

| Tùy chọn | Khóa | Có tác dụng ở |
|---|---|---|
| Bỏ dấu tự do | `vn_free_marking` | `TelexEngine.freeMarking` — mỗi phím |
| Telex đơn giản | `vn_simple_telex` | `TelexEngine.simpleTelex` — `w` đầu từ giữ nguyên |
| Quyết định theo ngữ cảnh | `vn_contextual_english` | `contextualEnglish` + `retroactiveEnglish`; sửa ngược ở `noteBoundaryCharacter` |
| Gợi ý sửa lỗi chạm trượt | `vn_auto_fix_adjacent` | `VietTelexHooks.addSuggestions` |
| Tự khôi phục từ Tiếng Anh | `vn_auto_restore` | `commitText(autoRestore)` lúc chốt từ, `eagerEnglishRestore` |
| Kiểm tra chính tả khi gõ | `vn_live_spell_check` | `TelexEngine.liveSpellCheck` — dừng ghép dấu khi không thể là tiếng Việt |
| Sửa dấu từ đã gõ | `vn_re_edit_words` | `beforeCodeInput`, `reopenAfterDelete`, `resumeWord` |

## 10. Kiểm thử

```mermaid
flowchart BT
    T1["TelexEngineOptionsTest<br/>(:viettelex-core, JVM thuần)<br/>engine theo từng tùy chọn"] --> T2
    T2["VietTelexIntegrationTest<br/>(Robolectric)<br/>combiner, WordComposer, sửa dấu"] --> T3
    T3["VietTelexInputLogicTest<br/>(Robolectric)<br/>gõ qua LatinIME/InputLogic thật"]
```

| Lớp test | Bắt được |
|---|---|
| Engine | Sai luật ghép dấu, kiểm tra âm tiết, tiếng Anh |
| Combiner | Sai cách combiner nạp/mở lại từ, đồng bộ cài đặt |
| InputLogic | **Hook ngừng được gọi** sau khi HeliBoard đổi luồng gõ — lớp quan trọng nhất khi cập nhật upstream (gỡ thử hook `finishBoundary` làm 3 test đỏ) |

Chạy:

```sh
./gradlew :viettelex-core:test :app:testDebugUnitTest \
    --tests 'helium314.keyboard.viettelex.*' --tests 'helium314.keyboard.latin.VietTelexInputLogicTest'
```

## 11. Theo kịp HeliBoard gốc

```mermaid
flowchart TD
    A["Actions → Sync upstream HeliBoard → Run workflow<br/>(hoặc scripts/sync-upstream.sh ở máy)"] --> B["fetch HeliBorg/HeliBoard<br/>main hoặc tag vX.Y"]
    B --> C{"main đã chứa bản này?"}
    C -- có --> Z["Dừng: up-to-date"]
    C -- chưa --> D["git merge vào bản sao của main"]
    D --> E{"Xung đột?"}
    E -- có --> I["Mở issue: danh sách file + hướng dẫn<br/>(exit 2)"]
    E -- không --> F["Chạy 21 test Tiếng Việt"]
    F --> G{"Qua?"}
    G -- qua --> H["Build APK, đẩy nhánh upstream-sync/…,<br/>mở PR"]
    G -- hỏng --> J["Vẫn mở PR, ghi rõ test hỏng,<br/>job báo đỏ (exit 3)"]
    H --> K["Cài APK, gõ thử, merge PR"]
```

Khi xung đột: lấy code của HeliBoard, rồi đặt lại các dòng `VietTelexHooks…` theo danh sách trong
[`UPSTREAM_SYNC.md`](UPSTREAM_SYNC.md). Không đưa logic Tiếng Việt trở lại file của HeliBoard.
