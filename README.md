# Digital Wellbeing (Android, Kotlin)

ফোনের ব্যবহার ট্র্যাক করে সীমা মানাতে সাহায্য করার অ্যাপ। HTML prototype-এর পূর্ণ Android রূপ।

## খোলার নিয়ম
1. Android Studio (Ladybug বা নতুন) খুলে এই ফোল্ডার Open করো, Gradle sync চালাও।
2. **আসল ফোনে** চালাও (emulator-এ Usage Access ও ব্যবহারের ডাটা প্রায় থাকে না)।
3. অ্যাপের "আজ" ট্যাবের সেটআপ তালিকা থেকে চালু করো: Usage Access, অন্য অ্যাপের ওপর দেখানো, Notification। ঐচ্ছিক: Accessibility, ব্যাটারি।
4. নিজের package নাম বদলাতে চাইলে `com.wellbeing.tracker` রিফ্যাক্টর করো (Studio: Refactor → Rename)।

## কী কী আছে
- স্ক্রিন টাইম, অ্যাপভিত্তিক সময়, আনলক সংখ্যা (UsageStats, নির্ভরযোগ্য)
- সীমা: স্ক্রিন টাইম, অ্যাপের সময়, কনটেন্ট সংখ্যা; ৮০% এ notification, ১০০% এ alarm
- Pause স্ক্রিন, Snooze (দিনে ২ বার), Strict Mode, Focus সেশন, Bedtime
- রিপোর্ট: ৭ দিনের চার্ট, দিনের কোন সময়ে ব্যবহার

## Prototype থেকে পার্থক্য
- Demo/simulate বোতাম নেই; আসল ডাটা আসে।
- শুরুতে ৫ ধাপের onboarding নেই; তার বদলে ঝুলে না থাকা সেটআপ তালিকা (Android permission সিস্টেম স্ক্রিন থেকেই দিতে হয়)।
- Pause/Block/Alarm একটি আলাদা Activity (`InterceptActivity`) দিয়ে দেখানো হয়, তাই "অন্য অ্যাপের ওপর দেখানো" permission লাগে।
- Bedtime-এর সময় বদলানো +/− (১৫ মিনিট) বোতামে, time picker নয়।
- Notification, Alarm, Pause-এর সেকেন্ড আর Strict Mode প্রোটোটাইপে "সীমা" ট্যাবে ছিল; এখানে "সেটিংস" ট্যাবে।
- রিপোর্ট আসল ডাটা থেকে (দিন/সপ্তাহ/মাস); প্রোটোটাইপের "উদাহরণ ডেটা" নেই। "মাস" মানে শেষ ২৮ দিন, ৪ সপ্তাহের দৈনিক গড়।
- ডেমো বোতাম (সিমুলেট, ১ সেকেন্ড = ১ মিনিট, ঘুমের সময় জোর করে চালু) নেই।
- লেখাগুলো `strings.xml`-এ নয়, কোডে সরাসরি আছে (পরে অনুবাদ করতে হলে সরাতে হবে)।

## জানা সীমাবদ্ধতা (সৎভাবে)
- **এই কোড কম্পাইল ও ফোনে চালিয়ে দেখা হয়নি** (যে পরিবেশে লেখা, সেখানে Android SDK ছিল না)। শুধু `domain` প্যাকেজ আলাদা করে কম্পাইল হয়ে ২৯টা unit test pass করেছে। প্রথম sync-এ ছোটখাটো compile error আসতে পারে; error আমাকে দিলে ঠিক করে দেব।
- Reels/Shorts/ভিডিও/Post গণনা **আনুমানিক**। `domain/ContentHeuristics.kt`-এর view ID মার্কারগুলো আন্দাজ; ফোনে প্রতিটা অ্যাপ খুলে মিলিয়ে ঠিক করতে হবে। অ্যাপ আপডেট হলে ভাঙতে পারে। সময়ের সীমাই মূল ভরসা।
- Android 9 (API 28)-এর নিচে স্ক্রিন-অন ও আনলকের ইভেন্ট নেই, তাই স্ক্রিন টাইম আনুমানিক।
- কিছু ফোনে (Xiaomi, Oppo, Vivo ইত্যাদি) ব্যাকগ্রাউন্ড সার্ভিস বন্ধ করে দেয়; ব্যাটারি "Unrestricted" ও Autostart চালু রাখো।
- Google Play-তে দিতে হলে Accessibility ব্যবহারের prominent disclosure, Data Safety ফর্ম, আর Usage/Overlay permission-এর ঘোষণা লাগবে। ব্যক্তিগত ব্যবহারের জন্য এখন দরকার নেই।

## ফোল্ডার
`domain/` লজিক (pure Kotlin, test আছে) · `data/` Room, DataStore, UsageStats · `engine/` নিয়ম ও alert · `service/` MonitorService, Accessibility, Boot · `intercept/` Pause/Block/Alarm · `ui/` Compose স্ক্রিন

## GitHub দিয়ে APK বানানো (Android Studio ছাড়া)
1. GitHub-এ নতুন (private) repo বানাও, এই ফোল্ডারের সব ফাইল push করো (`.github` ফোল্ডারসহ)।
2. repo-র **Actions** ট্যাবে "Build APK" চলবে (না চললে Run workflow চাপো)।
3. সবুজ হলে ওই run খুলে নিচে **Artifacts → app-debug-apk** ডাউনলোড করো, ফোনে install করো।
4. লাল হলে ওই run-এর লগের error লাইনগুলো আমাকে দাও।
