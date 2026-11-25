// Five distinct style demonstrations share each source. The holdout is in scenarios.json.
export interface ExampleFamily {
  id: string;
  language: string;
  mode: string;
  relationship: string;
  text: string;
  variants: Record<string, string>;
}

export const examples: ExampleFamily[] = [
  {
    "id": "ex001",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "colleague",
    "text": "মিটিংটা কাল করলে আমার সুবিধা হয়।",
    "variants": {
      "professional": "আমার জন্য আগামীকাল মিটিংটা করা সুবিধাজনক হবে। আপনার সময় মিলবে?",
      "respectful": "আপনার অসুবিধা না হলে মিটিংটা কি কাল করতে পারি?",
      "confident": "মিটিংটা কাল করার প্রস্তাব দিচ্ছি। আপনার কখন সুবিধা?",
      "mature": "আমার সময়টা কাল ভালো মেলে। সবার সময় মিললে তখনই বসি?",
      "subtle": "কাল বসার সুযোগ আছে কি?"
    }
  },
  {
    "id": "ex002",
    "language": "bn",
    "mode": "express",
    "relationship": "teacher",
    "text": "স্যারের কাছ থেকে খাতাটা দেখার সময় চাই",
    "variants": {
      "professional": "স্যার, আমার খাতাটা নিয়ে আপনার সঙ্গে কখন বসতে পারি?",
      "respectful": "স্যার, একটু সময় হলে আমার খাতাটা দেখার সুযোগ দেবেন?",
      "confident": "স্যার, খাতাটা দেখে কোথায় ভুল করেছি বুঝতে চাই। কখন আসব?",
      "mature": "স্যার, আপনার সুবিধামতো একদিন খাতাটা দেখে নিতে চাই।",
      "subtle": "স্যার, খাতাটা কি একবার দেখা যাবে?"
    }
  },
  {
    "id": "ex003",
    "language": "bn",
    "mode": "reply",
    "relationship": "stranger",
    "text": "বইটা কি এখনও আপনার কাছে আছে?",
    "variants": {
      "professional": "কোন বইটির কথা বলছেন জানালে দেখে বলতে পারব।",
      "respectful": "বইটার নামটা একটু মনে করিয়ে দেবেন? দেখে জানাই।",
      "confident": "নামটা বলুন, বইটা আছে কি না মিলিয়ে বলি।",
      "mature": "ভুল বইয়ের খবর দিতে চাই না। নামটা বলবেন?",
      "subtle": "কোন বইটা বলছেন?"
    }
  },
  {
    "id": "ex004",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "রেজাল্ট নিয়ে মন খারাপ হলে আমাকে বলিস।",
    "variants": {
      "comforting": "রেজাল্ট নিয়ে খারাপ লাগলে বলিস। আগে তোর কথাটা শুনি।",
      "emotional": "মনটা ভার হয়ে থাকলে আমার কাছে খুলে বলিস। রেজাল্ট নিয়েও বলতে পারিস।",
      "mature": "রেজাল্টটা নিয়ে তোর কেমন লাগছে জানতে চাই, যখন বলতে ইচ্ছে করবে।",
      "subtle": "কথা বলতে ইচ্ছে করলে বলিস, রেজাল্ট নিয়েও।",
      "respectful": "রেজাল্ট নিয়ে কথা বলবি কি না, সেটা তোর ইচ্ছা। চাইলে শুনব।"
    }
  },
  {
    "id": "ex005",
    "language": "bn",
    "mode": "reply",
    "relationship": "friend",
    "text": "আজ আম্মুর জন্য খুব মন খারাপ করছে।",
    "variants": {
      "comforting": "আম্মুর কথা খুব মনে পড়ছে বুঝি। উনার কথা বলতে চাইলে শুনছি।",
      "emotional": "পড়েই মনটা ভার হলো। আজ আম্মুর কোন কথাটা বেশি মনে পড়ছে?",
      "mature": "এমন দিনে কী বলা ঠিক বুঝি না। তুই চাইলে শুধু শুনব।",
      "subtle": "আম্মুর কথা বলতে ইচ্ছে করছে?",
      "respectful": "তোর যেমন স্বস্তি লাগে তেমন করেই বলিস। তাড়া নেই।"
    }
  },
  {
    "id": "ex006",
    "language": "bn",
    "mode": "express",
    "relationship": "family",
    "text": "বোনকে বলতে চাই প্রথম দিন নার্ভাস হওয়া স্বাভাবিক",
    "variants": {
      "comforting": "প্রথম দিন একটু নার্ভাস লাগতেই পারে। একসঙ্গে সব বুঝে ফেলতে হবে না।",
      "emotional": "তোর বুক ধড়ফড় করলেও তুই নিজের মতোই থাকিস। প্রথম দিন বলে কথা।",
      "mature": "নতুন জায়গায় একটু সময় লাগে। নার্ভাস লাগাটা স্বাভাবিক।",
      "subtle": "প্রথম দিন তো, একটু অস্বস্তি লাগতেই পারে রে।",
      "respectful": "তোর কেমন লাগছে তুই ভালো জানিস। প্রথম দিনের অস্বস্তিটা অস্বাভাবিক কিছু না।"
    }
  },
  {
    "id": "ex007",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "partner",
    "text": "তোমার গলার আওয়াজ শুনতে ইচ্ছে করছে।",
    "variants": {
      "flirty": "তোমার গলাটা শোনার একটা অজুহাত খুঁজছি। ফোন দেব?",
      "romantic": "তোমার কণ্ঠটা শুনতে খুব ইচ্ছে করছে। একটু কথা হবে?",
      "poetic": "আজ শব্দের ভিড়ে তোমার কণ্ঠটাই শুনতে চাই।",
      "bold": "তোমার গলা শুনতে চাই। সময় হলে ফোন দাও।",
      "subtle": "একটু কথা বলার সময় হবে আজ?"
    }
  },
  {
    "id": "ex008",
    "language": "bn",
    "mode": "reply",
    "relationship": "crush",
    "text": "আমার আঁকা ছবিটা কেমন লাগল?",
    "variants": {
      "flirty": "ছবিটা ভালো লেগেছে। এঁকেছ যে মানুষটা, তার সঙ্গেও একটু কথা বলতে ইচ্ছে করছে।",
      "romantic": "তোমার চোখ দিয়ে জিনিসটা কেমন দেখায়, ছবিটা দেখে একটু বুঝলাম। ভালো লেগেছে।",
      "poetic": "ছবিটা যেন তোমার চোখ দিয়ে দেখা একটা ছোট্ট জানালা।",
      "bold": "আমার ভালো লেগেছে। কী ভেবে এঁকেছিলে শুনতে চাই।",
      "subtle": "ছবিটায় একটু থেমে তাকাতে ইচ্ছে করে। কী ভেবে এঁকেছিলে?"
    }
  },
  {
    "id": "ex009",
    "language": "bn",
    "mode": "express",
    "relationship": "partner",
    "text": "একসঙ্গে নীরবে হাঁটার ইচ্ছে",
    "variants": {
      "flirty": "একটা হাঁটা পাওনা রাখতে পারি? পাশে তুমি থাকলে গল্প না হলেও চলবে।",
      "romantic": "তোমার পাশে একটু হাঁটতে চাই। চুপচাপ থাকলেও সেই সময়টা তোমার সঙ্গেই কাটবে।",
      "poetic": "কথারা বিশ্রাম নিক, তোমার পাশে পায়ের শব্দ নিয়ে হাঁটি।",
      "bold": "তোমার সঙ্গে হাঁটতে চাই, কোনো কথার চাপ ছাড়া।",
      "subtle": "একদিন চুপচাপ একটু হাঁটবে আমার সঙ্গে?"
    }
  },
  {
    "id": "ex010",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "দলের নাম ঠিক করতেই খেলা শেষ হয়ে যাবে।",
    "variants": {
      "funny": "নাম বাছাইয়ে ট্রফি থাকলে আমরা এতক্ষণে জিতে যেতাম।",
      "witty": "খেলার আগে নামের ফাইনালটাই শেষ করি চল।",
      "sarcastic": "বাহ, নামটা ঠিক হলেই বাকি তুচ্ছ কাজটা থাকবে। খেলা!",
      "savage": "আমাদের দলে সবচেয়ে ফিট জিনিসটা হলো নামের তালিকা।",
      "culturally inspired": "নাম নিয়ে এই আড্ডাই এক ইনিংস খেলে ফেলল রে।"
    }
  },
  {
    "id": "ex011",
    "language": "bn",
    "mode": "reply",
    "relationship": "friend",
    "text": "চায়ে চিনি দিইনি, এবার বোঝ আসল স্বাদ।",
    "variants": {
      "funny": "আচ্ছা, আজ চায়ের মৌখিক পরীক্ষা তাহলে!",
      "witty": "চিনি ছুটিতে, চা নিজের পরিচয় দিচ্ছে?",
      "sarcastic": "বাহ, আমার জিভের জন্য আজ বিশেষ প্রশিক্ষণ।",
      "savage": "চিনির বাজেট কাটার এত সুন্দর ভাষাও আছে দেখছি।",
      "culturally inspired": "চায়ের দোকানের আড্ডায় এই নিয়ে আজ দুই দল হবে।"
    }
  },
  {
    "id": "ex012",
    "language": "bn",
    "mode": "express",
    "relationship": "friend",
    "text": "বন্ধুর বিশাল প্লেলিস্ট নিয়ে মজা",
    "variants": {
      "funny": "তোর প্লেলিস্টটা শেষ করতে আলাদা ছুটি চাইতে হবে।",
      "witty": "প্লেলিস্ট দিলি, নাকি গানের লাইব্রেরির সদস্য করলি?",
      "sarcastic": "কী ছোট্ট তালিকা! বছরটা তো এখনও পুরোটাই বাকি।",
      "savage": "তোর প্লেলিস্টে ঢুকলে বেরোনোর জন্য মানচিত্র লাগে।",
      "culturally inspired": "যাওয়া-আসার বাসযাত্রা মিলিয়েও তোর গানের তালিকা ফুরোবে না।"
    }
  },
  {
    "id": "ex013",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "family",
    "text": "আব্বু, না জানিয়ে দেরি করেছি, সরি।",
    "variants": {
      "apologetic": "আব্বু, দেরি হবে আগে জানানো উচিত ছিল। দুঃখিত।",
      "mature": "আব্বু, দেরির চেয়ে না জানানোটা আমার ভুল হয়েছে।",
      "emotional": "আব্বু, তোমাকে অপেক্ষায় রেখেছি ভেবে খারাপ লাগছে। দেরির খবর দেওয়া উচিত ছিল।",
      "confident": "আব্বু, না জানিয়ে দেরি করেছি। ভুলটা আমার।",
      "respectful": "আব্বু, দেরির খবরটা আগে দিতে পারিনি বলে দুঃখিত।"
    }
  },
  {
    "id": "ex014",
    "language": "bn",
    "mode": "reply",
    "relationship": "friend",
    "text": "আমার কথা শেষ হওয়ার আগেই তুই উঠে গেলি।",
    "variants": {
      "apologetic": "তোর কথা শেষ হওয়ার আগেই চলে গেছি, সরি রে। এখন শুনতে পারি?",
      "mature": "তুই ঠিক বলেছিস। শেষ পর্যন্ত শোনা উচিত ছিল।",
      "emotional": "তুই বলছিলি আর আমি উঠে গেলাম। তোর কেমন লেগেছে ভাবলে খারাপ লাগছে।",
      "confident": "তোর কথাটা শেষ করতে দিইনি। এবার তুই বল, আমি শুনি।",
      "respectful": "ইচ্ছে হলে বাকি কথাটা বলিস। উঠে যাওয়াটা ঠিক করিনি।"
    }
  },
  {
    "id": "ex015",
    "language": "bn",
    "mode": "express",
    "relationship": "colleague",
    "text": "ভুল তারিখ লিখেছি, দুঃখ প্রকাশ",
    "variants": {
      "apologetic": "তারিখটা ভুল লিখেছি বলে দুঃখিত।",
      "mature": "তারিখের ভুলটা আমার। সঠিকটা মিলিয়ে নেওয়া দরকার।",
      "emotional": "আমার ভুল তারিখে বিভ্রান্তি হয়ে থাকতে পারে ভেবে খারাপ লাগছে।",
      "confident": "তারিখটা আমি ভুল লিখেছি। দায়টা আমার।",
      "respectful": "তারিখটা ভুল দেওয়ার জন্য দুঃখিত। অনুগ্রহ করে আগের তারিখটি ধরে নেবেন না।"
    }
  },
  {
    "id": "ex016",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "তাড়াতাড়ি আয়, জানালার পাশের জায়গাটা খালি।",
    "variants": {
      "witty": "জানালার পাশটা এখনো ফাঁকা, এই সুযোগটাকে বসিয়ে রাখিস না!",
      "funny": "জানালার সিটটা তোর জন্য হাই তুলছে, তাড়াতাড়ি আয়।",
      "culturally inspired": "বাসের জানালার সিট কিন্তু রোজ মেলে না, জলদি আয়!",
      "confident": "তাড়াতাড়ি আয়। জানালার পাশের জায়গাটা এখনো খালি।",
      "custom": "জলদি আয়, জানালার পাশে জায়গা আছে।"
    }
  },
  {
    "id": "ex017",
    "language": "bn",
    "mode": "reply",
    "relationship": "friend",
    "text": "ফুচকা খাবি, নাকি ঝালমুড়ি?",
    "variants": {
      "witty": "দুই পক্ষের যুক্তিই শক্ত। তোর মন কোনটার দিকে?",
      "funny": "এই প্রশ্নে পেট আর মন আলাদা কমিটি বসিয়েছে। তুই কী খাবি?",
      "culturally inspired": "ফুচকার টক আর ঝালমুড়ির ঝাল, দুটোই টানছে। তোর পছন্দ কী?",
      "confident": "আগে তোর পছন্দটা বল, তারপর ঠিক করি।",
      "custom": "তোর কোনটা খেতে ইচ্ছে করছে?"
    }
  },
  {
    "id": "ex018",
    "language": "bn",
    "mode": "express",
    "relationship": "friend",
    "text": "বৃষ্টির আগে বাজারটা সেরে ফেলতে বল",
    "variants": {
      "witty": "বৃষ্টির সঙ্গে দৌড়ের দরকার কী, বাজারটা আগে সারি চল।",
      "funny": "আকাশ বাজারের ব্যাগটা ভেজানোর আগেই চল রে!",
      "culturally inspired": "মেঘ দেখে মনে হচ্ছে কাঁচাবাজারটা আগে সারাই ভালো।",
      "confident": "বৃষ্টি আসার আগেই বাজারটা করে ফেলি।",
      "custom": "চল, বৃষ্টির আগে বাজারটা সারি।"
    }
  },
  {
    "id": "ex019",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "stranger",
    "text": "ভাড়া কত হবে আগে বলবেন?",
    "variants": {
      "professional": "যাওয়ার আগে ভাড়াটা জানালে সুবিধা হবে।",
      "respectful": "ভাই, যাওয়ার আগে ভাড়াটা একটু বলবেন?",
      "confident": "রওনা দেওয়ার আগে ভাড়াটা ঠিক করে নিই। কত হবে?",
      "mature": "ভাড়া আগে জেনে নিতে চাই, তাহলে দুজনেরই পরিষ্কার থাকবে।",
      "subtle": "আগে ভাড়াটা জানা যাবে?"
    }
  },
  {
    "id": "ex020",
    "language": "bn",
    "mode": "reply",
    "relationship": "teacher",
    "text": "জমা দেওয়ার আগে কোন অংশটা বুঝতে সমস্যা হচ্ছে বলো।",
    "variants": {
      "professional": "স্যার, যে অংশে আটকে আছি সেটা আপনাকে দেখিয়ে বুঝে নিতে চাই।",
      "respectful": "স্যার, অসুবিধার জায়গাটা দেখালে একটু বুঝিয়ে দেবেন?",
      "confident": "স্যার, সমস্যাটা নির্দিষ্ট করে আপনার সঙ্গে আলোচনা করতে চাই।",
      "mature": "স্যার, কোথায় আটকে যাচ্ছি সেটা আলাদা করে দেখাই, তাহলে বোঝাতে সুবিধা হবে।",
      "subtle": "স্যার, জায়গাটা একটু দেখাতে পারি?"
    }
  },
  {
    "id": "ex021",
    "language": "bn",
    "mode": "express",
    "relationship": "colleague",
    "text": "শুক্রবার ছুটির দিনে ফোন না করার অনুরোধ",
    "variants": {
      "professional": "শুক্রবার আমার ছুটির দিন। সেদিন ফোন করা থেকে বিরত থাকলে কৃতজ্ঞ থাকব।",
      "respectful": "অনুরোধ করছি, শুক্রবার ছুটির দিনে আমাকে ফোন করবেন না।",
      "confident": "শুক্রবার আমি ছুটিতে থাকি। ওই দিন ফোন নিতে চাই না।",
      "mature": "শুক্রবারটা ব্যক্তিগত সময় হিসেবে রাখি। সেদিন ফোন না করলেই সুবিধা হবে।",
      "subtle": "শুক্রবারটা কি ফোন ছাড়া রাখা যায়?"
    }
  },
  {
    "id": "ex022",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "কিছু বলতে না চাইলে না বললেও হবে।",
    "variants": {
      "comforting": "না বলতে চাইলে থাক। চুপ করে থাকলেও তোর পাশে আছি।",
      "emotional": "সব কথা মুখে আনতে হয় না রে। আমার কাছে চুপ করেও থাকতে পারিস।",
      "mature": "কতটা বলবি, কখন বলবি, সেটা তুই ঠিক করিস।",
      "subtle": "এখন বলতে ইচ্ছে না করলে থাক।",
      "respectful": "তোর ব্যক্তিগত কথা, শোনার জন্য জোর করব না।"
    }
  },
  {
    "id": "ex023",
    "language": "bn",
    "mode": "reply",
    "relationship": "family",
    "text": "নতুন বাসায় কিছুই আপন মনে হয় না।",
    "variants": {
      "comforting": "নতুন বাসায় নিজের মতো লাগতে সময় লাগে। কোন জিনিসটা বেশি মিস করছ?",
      "emotional": "চেনা বাড়ির ছোট ছোট জিনিসও খুব মনে পড়ে, তাই না?",
      "mature": "মানিয়ে নিতে তাড়াহুড়ো করার দরকার নেই। কী কী অচেনা লাগছে শুনতে চাই।",
      "subtle": "পুরনো বাসার কী বেশি মনে পড়ছে?",
      "respectful": "নতুন বাসা নিয়ে তোমার যেমন লাগছে তেমনই বলো, শুনছি।"
    }
  },
  {
    "id": "ex024",
    "language": "bn",
    "mode": "express",
    "relationship": "friend",
    "text": "পরীক্ষা শেষ, আগে বিশ্রাম নিতে বলি",
    "variants": {
      "comforting": "পরীক্ষা তো শেষ। আগে একটু জিরিয়ে নে, বাকি কথা পরে হবে।",
      "emotional": "এত চাপের পর নিজের জন্য একটু আরাম রাখিস রে।",
      "mature": "পরীক্ষা শেষ হয়েছে। এখন খানিকটা বিশ্রাম নিলেই ভালো লাগতে পারে।",
      "subtle": "এখন একটু জিরিয়ে নিবি?",
      "respectful": "ইচ্ছে হলে আগে বিশ্রাম নে। পরীক্ষার কথা পরে হলেও চলবে।"
    }
  },
  {
    "id": "ex025",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "crush",
    "text": "তুমি যে গানটা পাঠালে আবার শুনতে ইচ্ছে করছে।",
    "variants": {
      "flirty": "গানটা আবার শুনব। তোমার পছন্দটা একটু বিপজ্জনক রকম ভালো।",
      "romantic": "তোমার বেছে পাঠানো গানটার কাছে আবার ফিরতে ইচ্ছে করছে।",
      "poetic": "তোমার পাঠানো সুরটা শেষ হয়েও যেন একটু থেকে গেল।",
      "bold": "তোমার পাঠানো গানটা আবার শুনতে চাই।",
      "subtle": "গানটা আরেকবার চালালে মন্দ হয় না।"
    }
  },
  {
    "id": "ex026",
    "language": "bn",
    "mode": "reply",
    "relationship": "partner",
    "text": "আজ এত চুপচাপ কেন?",
    "variants": {
      "flirty": "চুপচাপ হলেই কি তোমার নজরে একটু বেশি পড়ি?",
      "romantic": "আমি চুপ করে থাকলেও তোমার সঙ্গে একটু সময় কাটাতে ভালো লাগে।",
      "poetic": "সব কথা আজ শব্দ হয়ে উঠছে না। পাশে একটু থাকবে?",
      "bold": "একটু চুপ থাকতে ইচ্ছে করছে। তোমার সঙ্গে আছি।",
      "subtle": "আজ একটু কম কথা বলি? পাশে থাকলে ভালো লাগবে।"
    }
  },
  {
    "id": "ex027",
    "language": "bn",
    "mode": "express",
    "relationship": "crush",
    "text": "একসঙ্গে বইমেলায় যাওয়ার প্রস্তাব, চাপ যেন না হয়",
    "variants": {
      "flirty": "বই পছন্দের অজুহাতে তোমার সঙ্গে মেলায় একটু হাঁটা যায়? সময় না মিললেও ঠিক আছে।",
      "romantic": "তোমার পছন্দের বই দেখতে দেখতে একদিন মেলায় হাঁটতে ইচ্ছে করছে। তুমি চাইলে।",
      "poetic": "বইমেলার ভিড়ে দুজনের জন্য একটু হাঁটার জায়গা হবে? ইচ্ছে হলেই বলো।",
      "bold": "তোমার সঙ্গে বইমেলায় যেতে চাই। তোমারও ইচ্ছে আছে?",
      "subtle": "একদিন বইমেলা ঘুরবে? সুবিধা না হলে আরেক সময়।"
    }
  },
  {
    "id": "ex028",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "ছবি তুলতে তুলতে খাবার ঠান্ডা হয়ে গেল।",
    "variants": {
      "funny": "খাবারটা ছবি তুলে মডেল হলো, খেতে গিয়ে দেখি ঠান্ডা!",
      "witty": "ছবিতে গরম, প্লেটে শীতকাল।",
      "sarcastic": "বাহ, ছবিগুলো উষ্ণই আছে নিশ্চয়ই। খাবারটা তো ঠান্ডা।",
      "savage": "খাবারটা খাওয়ার পরীক্ষা ফেল, ছবি তোলায় পাস।",
      "culturally inspired": "এত ছবি তুললাম যে গরম ভাতের ধোঁয়াটাও ছুটি নিল।"
    }
  },
  {
    "id": "ex029",
    "language": "bn",
    "mode": "reply",
    "relationship": "friend",
    "text": "আর একটা পর্ব দেখেই ঘুমাব।",
    "variants": {
      "funny": "তোর ওই একটা পর্বের আত্মীয়স্বজনও কি সঙ্গে আসবে?",
      "witty": "একটা পর্ব মানে ঘড়ির হিসাবে, না তোর হিসাবে?",
      "sarcastic": "হ্যাঁ হ্যাঁ, পরের পর্বের বোতামটাও নিশ্চয়ই ছুটিতে।",
      "savage": "তোর একটা পর্বের অঙ্কে ভোর হয়ে যায় রে।",
      "culturally inspired": "তোর ওই শেষ পর্বটা চায়ের দোকানের শেষ আড্ডার মতো লম্বা।"
    }
  },
  {
    "id": "ex030",
    "language": "bn",
    "mode": "express",
    "relationship": "friend",
    "text": "গ্রুপে সবাই পড়ে কিন্তু কেউ উত্তর দেয় না",
    "variants": {
      "funny": "এই গ্রুপে পড়ার অভ্যাস দারুণ, লেখার হাতটা এবার খুলবি কেউ?",
      "witty": "পাঠকসমাজ, একজন লেখক পাওয়া যাবে?",
      "sarcastic": "চমৎকার আলোচনা, নীরবতায় প্রত্যেকে খুব সক্রিয়।",
      "savage": "এত পাঠক নিয়েও গ্রুপটা এক লাইন উত্তর তুলতে পারে না।",
      "culturally inspired": "পাড়ার আড্ডা হলে এতক্ষণে সবাই একসঙ্গে বলত, এখানে একটা উত্তরও নেই!"
    }
  },
  {
    "id": "ex031",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "partner",
    "text": "তোমার কথা না শুনে মত দিয়েছি, খারাপ লাগছে।",
    "variants": {
      "apologetic": "তোমার কথা পুরোটা না শুনেই মত দিয়েছি। দুঃখিত।",
      "mature": "আগে তোমার কথাটা বোঝা দরকার ছিল। আমি তাড়াহুড়ো করে মত দিয়েছি।",
      "emotional": "তুমি বলছিলে, আমি শুনতে পারিনি মন দিয়ে। এটা ভেবে খারাপ লাগছে।",
      "confident": "আগে মত দিয়ে ফেলাটা আমার ভুল। এবার তোমার কথাটা শুনতে চাই।",
      "respectful": "তোমার কথা শেষ না হতেই মত দেওয়া ঠিক হয়নি। ইচ্ছে হলে বাকিটা বলো।"
    }
  },
  {
    "id": "ex032",
    "language": "bn",
    "mode": "reply",
    "relationship": "colleague",
    "text": "ফাইলে আমার নামটা বাদ পড়েছে।",
    "variants": {
      "apologetic": "আপনার নাম বাদ পড়েছে বলে দুঃখিত। ঠিক বানানটা মিলিয়ে যোগ করতে চাই।",
      "mature": "এটা চোখ এড়িয়ে গেছে। নামটা কীভাবে লিখব, একবার মিলিয়ে নিই?",
      "emotional": "আপনার নাম বাদ পড়ায় খারাপ লাগছে। সঠিকভাবে যোগ করতে চাই।",
      "confident": "আপনার নামটা যোগ করা দরকার। বানানটা মিলিয়ে নিই।",
      "respectful": "জানানোর জন্য ধন্যবাদ। আপনার নামটা যেভাবে চান সেভাবে যোগ করতে চাই।"
    }
  },
  {
    "id": "ex033",
    "language": "bn",
    "mode": "express",
    "relationship": "friend",
    "text": "বন্ধুর ধার দেওয়া কলমটা হারিয়েছি",
    "variants": {
      "apologetic": "তোর ধার দেওয়া কলমটা হারিয়ে ফেলেছি, সরি রে।",
      "mature": "তোর কলমটা হারিয়েছি। কীভাবে ঠিক করলে তোর সুবিধা হবে বলিস।",
      "emotional": "তোর জিনিসটা রাখতে পারলাম না, খুব খারাপ লাগছে। কলমটা হারিয়েছি।",
      "confident": "কলমটা আমার কাছেই হারিয়েছে। দায়টা আমার।",
      "respectful": "তোর ধার দেওয়া কলমটা হারিয়ে ফেলেছি। কী করলে ভালো হয় জানাবি?"
    }
  },
  {
    "id": "ex034",
    "language": "bn",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "চা খেতে গেলে আমাকে ডেকে নিস।",
    "variants": {
      "witty": "চায়ের দলে একটা জায়গা রাখিস, যাওয়ার সময় ডাক দিস।",
      "funny": "চা কমিটির বৈঠক বসলে আমাকেও ডাকিস!",
      "culturally inspired": "টংয়ে চা খেতে বের হলে একটা হাঁক দিস।",
      "confident": "চা খেতে যাওয়ার আগে আমাকে ডাকিস।",
      "custom": "চা খেতে গেলে ডাকিস।"
    }
  },
  {
    "id": "ex035",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "colleague",
    "text": "Apni slide ta dekhe aj feedback dite parben?",
    "variants": {
      "professional": "Aj slide-ta review kore feedback dewa ki possible hobe?",
      "respectful": "Apnar shomoy hole aj slide-ta dekhe ektu feedback deben?",
      "confident": "Slide-ta niye aj apnar feedback chai. Shomoy hobe?",
      "mature": "Aj feedback pele slide-ta niye egote subidha hobe. Dekhar shomoy paben?",
      "subtle": "Aj slide-ta ekbar dekha jabe?"
    }
  },
  {
    "id": "ex036",
    "language": "banglish",
    "mode": "express",
    "relationship": "teacher",
    "text": "Miss ke class note chaite hobe",
    "variants": {
      "professional": "Miss, class-er note-ta ki share kora jabe?",
      "respectful": "Miss, apnar subidha hole class-er note-ta ektu pete pari?",
      "confident": "Miss, class-er note-ta chai. Kibhabe pete pari?",
      "mature": "Miss, note-ta pele class-er bishoygulo miliye nite parbo. Share korben?",
      "subtle": "Miss, class-er note-ta ki pawa jabe?"
    }
  },
  {
    "id": "ex037",
    "language": "banglish",
    "mode": "reply",
    "relationship": "stranger",
    "text": "Apnader dokan koyta porjonto khola?",
    "variants": {
      "professional": "Kon diner shomoyta jante chacchen? Milie janate parbo.",
      "respectful": "Apni kon diner kotha bolchen ektu janaben?",
      "confident": "Din-ta bolun, khola thakar shomoyta dekhe boli.",
      "mature": "Shomoyta bhul bolte chai na. Kon din ashben?",
      "subtle": "Kon diner timing jante chacchen?"
    }
  },
  {
    "id": "ex038",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "Viva ta niye eto chap nish na re.",
    "variants": {
      "comforting": "Viva niye chap lagle bolish. Ekdom eka shamlate hobe na.",
      "emotional": "Tor viva niye tension-ta bujhte parchi re. Kotha bolbi?",
      "mature": "Viva niye chinta howa shabhabik. Kon jaygata niye beshi chap?",
      "subtle": "Viva niye ektu kotha bolbi?",
      "respectful": "Viva niye tor jemon lagche temon korei bolish, shunte chai."
    }
  },
  {
    "id": "ex039",
    "language": "banglish",
    "mode": "reply",
    "relationship": "friend",
    "text": "Bus miss korlam, mejaj ekdom kharap.",
    "variants": {
      "comforting": "Uff re, bus miss korle mejaj kharap hobei. Ektu dom ne.",
      "emotional": "Ish, ki birokto lagche tor bujhte parchi. Bol, shunchi.",
      "mature": "Ekhon tor ki dorkar, ektu khobor nibo naki shudhu shunbo?",
      "subtle": "Uff, bus-er ki jhamela! Ekhon kemon achish?",
      "respectful": "Birokto lagar motoi byapar. Bolte ichcha korle bolish."
    }
  },
  {
    "id": "ex040",
    "language": "banglish",
    "mode": "express",
    "relationship": "family",
    "text": "Bhaiya ke bolbo chakri khojar chape eka na",
    "variants": {
      "comforting": "Bhaiya, job khujte khujte chap lagle amake bolte paro. Eka shamlate hobe na.",
      "emotional": "Bhaiya, tomar din kemon jacche shunte chai, shudhu job-er khobor na.",
      "mature": "Bhaiya, job khonjar shomoyta kothin. Tomar moto kore bolo, ami shunchi.",
      "subtle": "Bhaiya, job niye chap lagche? Ichcha hole bolo.",
      "respectful": "Bhaiya, jototuku share korte bhalo lage tototukui bolo. Ami achi."
    }
  },
  {
    "id": "ex041",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "crush",
    "text": "Tomar sathe golpo korle adda chharte mon chay na.",
    "variants": {
      "flirty": "Tomar shathe golpo shuru korlei amar bye bolar skill hariye jay.",
      "romantic": "Tomar shathe adda-ta amar eto bhalo lage je shesh korte mon chay na.",
      "poetic": "Tomar golper kache shomoyta ektu theme thakle mondo hoto na.",
      "bold": "Tomar shathe aro golpo korte chai. Adda chharte ichcha kore na.",
      "subtle": "Tomar shathe golpo-ta arektu choluk?"
    }
  },
  {
    "id": "ex042",
    "language": "banglish",
    "mode": "reply",
    "relationship": "partner",
    "text": "Coffee khete jabo ekdin?",
    "variants": {
      "flirty": "Coffee-r lobh ache, tobe tomar company-ta beshi tempting.",
      "romantic": "Tomar shathe coffee-r shomoyta chai. Kobe gele tomar bhalo hoy?",
      "poetic": "Ek cup coffee ar tomar shathe dhire dhire goriye jawa ekta bikel. Kobe jabe?",
      "bold": "Jabo. Tomar shathe coffee khete chai, din thik kori?",
      "subtle": "Mondo hoy na. Kon din tomar shubidha?"
    }
  },
  {
    "id": "ex043",
    "language": "banglish",
    "mode": "express",
    "relationship": "partner",
    "text": "Tomar haat dhorte chai, age jiggesh kori",
    "variants": {
      "flirty": "Haat-ta dhorar permission pele amar hatar speed emnitei kome jabe. Dhori?",
      "romantic": "Tomar haat dhore ektu hatte ichcha korche. Tomar bhalo lagbe?",
      "poetic": "Hatar pothe tomar haat-ta amar haater pashe rakhbe, jodi ichcha hoy?",
      "bold": "Tomar haat dhorte chai. Tumi chailei dhorbo.",
      "subtle": "Haat-ta dhorte pari?"
    }
  },
  {
    "id": "ex044",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "Tui alarm diyeo uthosh na.",
    "variants": {
      "funny": "Tor alarm-er kaj mone hoy shudhu nijeke jagano.",
      "witty": "Alarm punctual, tui flexible.",
      "sarcastic": "Darun teamwork. Alarm baje, tui ghumash.",
      "savage": "Tor alarm-er performance review hole doshta tor gharei porbe.",
      "culturally inspired": "Tor alarm-ta ghum bhanganor cheye cha-er biratir ghontar moto kaj kore!"
    }
  },
  {
    "id": "ex045",
    "language": "banglish",
    "mode": "reply",
    "relationship": "friend",
    "text": "Amar porar table ta shundor kore shajalam.",
    "variants": {
      "funny": "Table toh ready, boi kholar ribbon katbi kokhon?",
      "witty": "Table-er preparation complete. Student-er ki khobor?",
      "sarcastic": "Bah, table-ta exam dile full marks pabe.",
      "savage": "Table-ta tor cheye age syllabus shuru korbe naki?",
      "culturally inspired": "Table shajano shesh, ebar ek cup cha diye pora shuru?"
    }
  },
  {
    "id": "ex046",
    "language": "banglish",
    "mode": "express",
    "relationship": "friend",
    "text": "Group photo te shobai alada dike takay",
    "variants": {
      "funny": "Amader group photo te camera-ta chara shobar dike keu na keu takay.",
      "witty": "Ek frame, panchta direction. Teamwork-e diversity ache!",
      "sarcastic": "Camera shamne chhilo, tao shobai nijer vision follow korlo.",
      "savage": "Amader group photo compass hishabe becha jabe.",
      "culturally inspired": "Amader group photo dekhe mone hoy shobai alada adday daak peyechhe."
    }
  },
  {
    "id": "ex047",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "Tor birthday te wish korte bhule gechi, sorry.",
    "variants": {
      "apologetic": "Tor birthday-te wish korte bhule gechi re. Mon theke sorry.",
      "mature": "Tor birthday miss korechi. Ojuhat na diye boli, bhulta amar.",
      "emotional": "Tor birthday-te wish korte bhulechi bhebei kharap lagche re.",
      "confident": "Wish korte bhule gechi. Bhulta amar, sorry.",
      "respectful": "Tor birthday-te wish kora hoyni bole khub dukkho lagche. Belated shubhechha."
    }
  },
  {
    "id": "ex048",
    "language": "banglish",
    "mode": "reply",
    "relationship": "partner",
    "text": "Amar message ta poreo reply dila na.",
    "variants": {
      "apologetic": "Poreo reply diyni, sorry. Tomake opekkhay rakha thik hoyni.",
      "mature": "Message pore chup chilam. Tomar kemon legeche shunte chai.",
      "emotional": "Tumi ekta uttor cheyechhile, ami chup chilam. Eta bhebe kharap lagche.",
      "confident": "Haan, porechhilam ar reply diyni. Amar bola uchit chhilo.",
      "respectful": "Reply na dewar jonno dukkhito. Tumi chaile ekhon kotha boli."
    }
  },
  {
    "id": "ex049",
    "language": "banglish",
    "mode": "express",
    "relationship": "family",
    "text": "Apu r charger na bole niyechi",
    "variants": {
      "apologetic": "Apu, na bole tomar charger-ta niyechi. Sorry.",
      "mature": "Apu, tomar charger newar age jiggesh kora uchit chhilo.",
      "emotional": "Apu, na bole charger niyechi bole kharap lagche.",
      "confident": "Apu, charger-ta ami niyechi. Age bola uchit chhilo, bhulta amar.",
      "respectful": "Apu, tomar permission chara charger newa thik hoyni. Dukkho prokash korchi."
    }
  },
  {
    "id": "ex050",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "Kalke practice e ball ante bhulish na.",
    "variants": {
      "witty": "Kal practice-e ball-ta chai. Shudhu player diye khela cholbe na!",
      "funny": "Kal ball-tao jeno practice-er invitation pay!",
      "culturally inspired": "Kal math-er khelar ashol mehman ball-ta, mone kore niye ashish.",
      "confident": "Kal practice-e ball-ta mone kore anish.",
      "custom": "Kal ball-ta ante bhulish na."
    }
  },
  {
    "id": "ex051",
    "language": "banglish",
    "mode": "reply",
    "relationship": "friend",
    "text": "Rickshaw nibi naki hatbi?",
    "variants": {
      "witty": "Pa ar pocket, kar vote age nibi?",
      "funny": "Amar pa-keo ki meeting-e daka jabe?",
      "culturally inspired": "Rickshaw-er bhara ar hatar poth-ta age milie dekhi?",
      "confident": "Age poth-ta koto dur dekhi, tarpor thik kori.",
      "custom": "Koto dur hatte hobe?"
    }
  },
  {
    "id": "ex052",
    "language": "banglish",
    "mode": "express",
    "relationship": "friend",
    "text": "Plan final korar age shobar time nite chai",
    "variants": {
      "witty": "Ghorigulo age ekmot hok, plan tarpor final.",
      "funny": "Plan bananor age shobar calendar-er shakkhatkar ni!",
      "culturally inspired": "Addar shomoy thik korar age shobar free slot-ta shuni.",
      "confident": "Shobai kokhon free bolo, tarpor plan final kori.",
      "custom": "Age shobar shomoyta bolo."
    }
  },
  {
    "id": "ex053",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "stranger",
    "text": "Photo ta group e dewar age permission niben.",
    "variants": {
      "professional": "Group-e chobi share korar age amar permission neben.",
      "respectful": "Amar chobita group-e dewar age ektu jiggesh korben, please.",
      "confident": "Amar permission niyei chobita group-e deben.",
      "mature": "Chobita kothay jacche seta ami age jante chai. Group-e dewar age bolben.",
      "subtle": "Group-e chobita dewar age amake ektu janaben?"
    }
  },
  {
    "id": "ex054",
    "language": "banglish",
    "mode": "reply",
    "relationship": "colleague",
    "text": "Ajke overnight shift ta cover korte parben?",
    "variants": {
      "professional": "Shift-er shomoyta janale amar availability check kore bolbo.",
      "respectful": "Shuru ar shesher shomoyta ektu bolben? Dekhe janai.",
      "confident": "Confirm korar age overnight shift-er exact shomoyta chai.",
      "mature": "Age shomoyta milate chai, tarpor nishchit kore janabo.",
      "subtle": "Shift-ta koyta theke koyta?"
    }
  },
  {
    "id": "ex055",
    "language": "banglish",
    "mode": "express",
    "relationship": "teacher",
    "text": "Assignment er feedback bujhte pari nai, miss ke jiggesh",
    "variants": {
      "professional": "Miss, assignment-er feedback-ta niye ektu kotha bolte chai. Amar ekta jayga porishkar hoyni.",
      "respectful": "Miss, feedback-ta arekbar bujhiye deben? Ekta jaygay atke gechi.",
      "confident": "Miss, feedback-ta thik bhabe bujhte chai. Kokhon jiggesh korte pari?",
      "mature": "Miss, bhul bujhe change korar age feedback-ta apnar shathe milie nite chai.",
      "subtle": "Miss, feedback niye ektu jiggesh korte pari?"
    }
  },
  {
    "id": "ex056",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "family",
    "text": "Nanu, klanto lagle boshun.",
    "variants": {
      "comforting": "Nanu, klanto lagle ektu boshe jiran.",
      "emotional": "Nanu, apnar koshto hok chai na. Klanto lagle boshen ektu.",
      "mature": "Nanu, nijer shorirer kotha shunen. Klanto lagle ektu boshen.",
      "subtle": "Nanu, ektu boshben?",
      "respectful": "Nanu, apnar jodi aram lage taile ektu boshen."
    }
  },
  {
    "id": "ex057",
    "language": "banglish",
    "mode": "reply",
    "relationship": "friend",
    "text": "Dhakai eshe kaukei chini na.",
    "variants": {
      "comforting": "Notun shohore kauke na chinle eka lage re. Kotha bolte chash?",
      "emotional": "Dhakar eto bhirer moddheo nijer manush na pele mon faka lage, tai na?",
      "mature": "Notun jaygay porichoy hote shomoy lage. Tor din kemon jacche?",
      "subtle": "Notun jaygata kemon lagche re?",
      "respectful": "Tor nijer gotite manush chinbi. Ekhon chaile tor kotha shunbo."
    }
  },
  {
    "id": "ex058",
    "language": "banglish",
    "mode": "express",
    "relationship": "partner",
    "text": "Tumi nijer moto shomoy nite paro",
    "variants": {
      "comforting": "Tomar nijer moto ektu shomoy lagle nao, kono tara nei.",
      "emotional": "Tomar mon-ta ektu dom nik. Jotota shomoy lage nio.",
      "mature": "Nijer shomoyta tomar. Kokhon kotha bolbe tumi thik koro.",
      "subtle": "Ektu shomoy lagle nio.",
      "respectful": "Tomar space-ta ami shomman kori. Tumi ready hole kotha hobe."
    }
  },
  {
    "id": "ex059",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "crush",
    "text": "Tomar hashita amar khub pochondo.",
    "variants": {
      "flirty": "Tomar hashi-ta ekebare unfair, dekhei amar mukheo hashi ashe.",
      "romantic": "Tomar hashi-ta amar boro priyo.",
      "poetic": "Tomar hashi-te diner bhitor ektu alo pore.",
      "bold": "Tomar hashi amar khub bhalo lage. Boltei ichcha holo.",
      "subtle": "Tomake hashle besh lage."
    }
  },
  {
    "id": "ex060",
    "language": "banglish",
    "mode": "reply",
    "relationship": "partner",
    "text": "Amar jonno ekta naam bhabo toh.",
    "variants": {
      "flirty": "Emon naam chai jeta daklei tumi ektu beshi hashbe. Ki bolo?",
      "romantic": "Tomar jonno emon ekta naam bhabte chai jeta shunlei apon lage.",
      "poetic": "Naam-ta chhoto hok, dakar moddhe ektu alo thakuk. Alo kemon?",
      "bold": "Alo bole dakte chai. Tomar pochondo?",
      "subtle": "Alo naam-ta kemon lage tomar?"
    }
  },
  {
    "id": "ex061",
    "language": "banglish",
    "mode": "express",
    "relationship": "partner",
    "text": "Bheer eo tomar pashe thakte bhalo lage",
    "variants": {
      "flirty": "Bheer beshi holeo tomar pasher seat-ta amar pochondo.",
      "romantic": "Bheer-er moddheo tomar pashe thakle apon lage.",
      "poetic": "Charpashe eto shobdo, tomar pashe dariye ektu nirobota pai.",
      "bold": "Bheer hok, tomar pashei thakte bhalo lage amar.",
      "subtle": "Tomar pashe thakle bheer-ta ektu kom lage."
    }
  },
  {
    "id": "ex062",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "Tui menu dekhei adha ghonta par korish.",
    "variants": {
      "funny": "Tor menu pora dekhe waiter-er-o intermission lage.",
      "witty": "Tor order-er appetizer holo adha ghonta research.",
      "sarcastic": "Ki druto decision! Menu-r cover-ta toh dekha shesh.",
      "savage": "Tui menu porish emon kore jeno seshe viva ache.",
      "culturally inspired": "Tor menu bachha shesh hote hote cha-er ditiyo round lagbe."
    }
  },
  {
    "id": "ex063",
    "language": "banglish",
    "mode": "reply",
    "relationship": "friend",
    "text": "Aj theke phone kom chalabo, post diye janalam.",
    "variants": {
      "funny": "Phone kom chalano dibosh-er live update-o pabo naki?",
      "witty": "Offline howar online notice peye gelam.",
      "sarcastic": "Oboshshoi, phone rakhte age aro ekta status dorkar.",
      "savage": "Tor digital break-er press conference-ta besh holo.",
      "culturally inspired": "Adday eshe phone-ta pocket-e rakhlei khobor-ta bishshash korbo!"
    }
  },
  {
    "id": "ex064",
    "language": "banglish",
    "mode": "express",
    "relationship": "friend",
    "text": "Friend er shortcut e rasta bere jay",
    "variants": {
      "funny": "Tor shortcut-e gele rastar mileage bonus pai.",
      "witty": "Tor shortcut-e shudhu naam-tai short.",
      "sarcastic": "Darun shortcut, shohor-ta ghura-o hoye gelo.",
      "savage": "Tor shortcut Google Map-keo chhutir abedon lekhabe.",
      "culturally inspired": "Tor shortcut-e rickshaw-wala-o bhara abar hisheb korbe."
    }
  },
  {
    "id": "ex065",
    "language": "banglish",
    "mode": "rewrite",
    "relationship": "colleague",
    "text": "Bhul version pathaisi eta amar bhul.",
    "variants": {
      "apologetic": "Bhul version-ta pathiyechi, ami dukkhito.",
      "mature": "Version-ta milano uchit chhilo. Bhul file amar thekei geche.",
      "emotional": "Bhul version-e jhamela hoye thakle kharap lagche. Ami pathiyechi.",
      "confident": "Bhul version pathanor day amar.",
      "respectful": "Bhul version share korar jonno khoma chaichi."
    }
  },
  {
    "id": "ex066",
    "language": "banglish",
    "mode": "reply",
    "relationship": "friend",
    "text": "Tor kothay amar lojja lagse.",
    "variants": {
      "apologetic": "Tor lojja lagar moto kotha bolechi, sorry re.",
      "mature": "Kon kothata kemon legeche bolbi? Age shunte chai.",
      "emotional": "Amar kothay tor lojja lagse shune kharap lagche.",
      "confident": "Ami ja bolechi tar day amar. Tor kotha shunbo.",
      "respectful": "Bolte ichcha korle bolish. Tor onubhutita amar kache guruttopurno."
    }
  },
  {
    "id": "ex067",
    "language": "banglish",
    "mode": "express",
    "relationship": "friend",
    "text": "Called after midnight, sorry bolbo",
    "variants": {
      "apologetic": "Raat barotar por call diyechi, sorry re.",
      "mature": "Eto raate call korar age jiggesh kora uchit chhilo.",
      "emotional": "Raate phone diye tor oshubidha korechi bhebe kharap lagche.",
      "confident": "Barotar por call diyechi. Shomoyta bechhe newa amar bhul.",
      "respectful": "Raat barotar por phone dewar jonno dukkhito. Tor shomoyta kheyal rakha uchit chhilo."
    }
  },
  {
    "id": "ex068",
    "language": "en",
    "mode": "rewrite",
    "relationship": "colleague",
    "text": "Can we go over the estimate before we share it?",
    "variants": {
      "professional": "Could we review the estimate together before sharing it?",
      "respectful": "Would you have a moment to check the estimate with me before it goes out?",
      "confident": "I'd like us to review the estimate together before we share it.",
      "mature": "I'd feel better sharing the estimate once we've both had a look.",
      "subtle": "Could we give the estimate one more look together first?"
    }
  },
  {
    "id": "ex069",
    "language": "en",
    "mode": "express",
    "relationship": "teacher",
    "text": "Ask my teacher whether diagrams can be in pencil",
    "variants": {
      "professional": "May I use pencil for the diagrams in my submission?",
      "respectful": "Would it be all right if I drew the diagrams in pencil?",
      "confident": "I'd like to draw the diagrams in pencil. Is that allowed?",
      "mature": "I'd like to check the drawing requirements before I start. Is pencil fine for the diagrams?",
      "subtle": "Is pencil okay for the diagrams?"
    }
  },
  {
    "id": "ex070",
    "language": "en",
    "mode": "reply",
    "relationship": "colleague",
    "text": "Which spreadsheet did you mean?",
    "variants": {
      "professional": "Could you send the filenames you're choosing between so I can identify the right one?",
      "respectful": "Would you mind sharing the filenames? I can point you to the one I meant.",
      "confident": "Send me the filenames and I'll tell you which one I meant.",
      "mature": "Let's make sure we're looking at the same sheet. What are the files called?",
      "subtle": "What filenames are you looking at?"
    }
  },
  {
    "id": "ex071",
    "language": "en",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "You can vent to me if the commute gets to you.",
    "variants": {
      "comforting": "If the commute wears you down, you can unload a bit with me.",
      "emotional": "Even a trip home can take so much out of you. Tell me about it if you feel like it.",
      "mature": "If you want to talk about the commute, I'm happy just to listen.",
      "subtle": "Rough ride home? I'm here if you want to talk.",
      "respectful": "If talking about the commute helps, I'd like to listen."
    }
  },
  {
    "id": "ex072",
    "language": "en",
    "mode": "reply",
    "relationship": "family",
    "text": "Everyone keeps asking what I'm doing after graduation.",
    "variants": {
      "comforting": "That's a lot of questions to sit through. You don't owe everyone a plan today.",
      "emotional": "It must get tiring to feel like every conversation is about what comes next.",
      "mature": "You can take your time figuring it out. Do you want to talk or change the subject?",
      "subtle": "Would you like a break from talking about graduation?",
      "respectful": "I'll leave that question with you. What would you rather talk about?"
    }
  },
  {
    "id": "ex073",
    "language": "en",
    "mode": "express",
    "relationship": "partner",
    "text": "Offer to listen without trying to solve the whole thing",
    "variants": {
      "comforting": "If you want to let it out, I'll listen. We can leave the fixing for another time.",
      "emotional": "I want to hear how it felt for you, even if there's nothing I can fix.",
      "mature": "Would it help if I simply listened this time?",
      "subtle": "Want me to just listen a bit?",
      "respectful": "Tell me as much as you're comfortable sharing. I'll hold off on advice."
    }
  },
  {
    "id": "ex074",
    "language": "en",
    "mode": "rewrite",
    "relationship": "crush",
    "text": "I like how you tell stories.",
    "variants": {
      "flirty": "You make even the setup worth staying for. Can I hear another story?",
      "romantic": "I could happily spend an evening listening to the way you tell a story.",
      "poetic": "You give a story room to breathe. I like listening to you.",
      "bold": "I really like the way you tell stories. I'd love to hear more.",
      "subtle": "There's something lovely about how you tell a story."
    }
  },
  {
    "id": "ex075",
    "language": "en",
    "mode": "reply",
    "relationship": "partner",
    "text": "Should I save you the corner piece?",
    "variants": {
      "flirty": "You know how to flirt with someone who likes the corner piece. Yes, please.",
      "romantic": "I'd love that. It's sweet that you thought of me.",
      "poetic": "Save me the little corner. I like that it comes with a thought of me.",
      "bold": "Yes, please save me that corner piece. I want it.",
      "subtle": "A corner piece sounds lovely, thank you."
    }
  },
  {
    "id": "ex076",
    "language": "en",
    "mode": "express",
    "relationship": "crush",
    "text": "Invite them to pick a song for the ride, keep it light",
    "variants": {
      "flirty": "Fancy claiming the DJ seat? I'd like a song picked by you for the ride.",
      "romantic": "It'd be nice to have a song you chose playing on the ride.",
      "poetic": "Pick a song for the ride? Let the road sound a little like you.",
      "bold": "I want you to pick a song for the ride. Up for it?",
      "subtle": "Got a song you'd put on for the ride?"
    }
  },
  {
    "id": "ex077",
    "language": "en",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "Our group makes a new poll for every tiny decision.",
    "variants": {
      "funny": "At this rate we'll need a poll to decide who makes the next poll.",
      "witty": "One group, zero decisions without a ballot.",
      "sarcastic": "Excellent. Democracy has reached the choice of biscuits.",
      "savage": "Our group could turn picking a spoon into election season.",
      "culturally inspired": "Give this group a cha order and it'll come back with three polls."
    }
  },
  {
    "id": "ex078",
    "language": "en",
    "mode": "reply",
    "relationship": "friend",
    "text": "I packed just the essentials. Two bags.",
    "variants": {
      "funny": "Two bags? Did the essentials invite their cousins?",
      "witty": "Minimalism, now available as a two-bag set.",
      "sarcastic": "Perfectly light travel. The second bag must just be for balance.",
      "savage": "Your essentials have a more generous definition of essential than mine.",
      "culturally inspired": "Two bags of essentials? Even the rickshaw seat needs a reservation."
    }
  },
  {
    "id": "ex079",
    "language": "en",
    "mode": "express",
    "relationship": "friend",
    "text": "Tease our friends about choosing a movie for longer than watching one",
    "variants": {
      "funny": "We should make a film about us choosing a film. There's plenty of footage.",
      "witty": "Our longest feature is the selection process.",
      "sarcastic": "Lovely movie night. The menu gave an award-worthy performance.",
      "savage": "Our watchlist has done more work tonight than the play button.",
      "culturally inspired": "At this rate there's time for another round of cha before the opening scene."
    }
  },
  {
    "id": "ex080",
    "language": "en",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "Sorry I shared the joke after you asked me to stop.",
    "variants": {
      "apologetic": "You asked me to stop and I still shared it. I'm sorry.",
      "mature": "You set a boundary and I ignored it. I was wrong to share the joke.",
      "emotional": "It feels awful knowing you asked me to stop and I didn't listen. I'm sorry.",
      "confident": "Sharing it was my call and it was wrong. I'm sorry.",
      "respectful": "You had already said no. I should have respected that, and I'm sorry."
    }
  },
  {
    "id": "ex081",
    "language": "en",
    "mode": "reply",
    "relationship": "partner",
    "text": "That felt more like a lecture than a conversation.",
    "variants": {
      "apologetic": "I'm sorry I talked at you. Would you like to tell me what I missed?",
      "mature": "I took over the conversation. I'd like to hear your side if you want to share it.",
      "emotional": "It hurts to think I made you feel talked down to. I'm sorry.",
      "confident": "I spoke over the conversation. I'll stop here and listen.",
      "respectful": "Thank you for telling me. Would you like to say more, or take a break?"
    }
  },
  {
    "id": "ex082",
    "language": "en",
    "mode": "express",
    "relationship": "colleague",
    "text": "Apologize for spelling a teammate's surname wrong",
    "variants": {
      "apologetic": "I'm sorry I misspelled your surname.",
      "mature": "I got your surname wrong. Could you confirm the spelling so I get it right?",
      "emotional": "I'm sorry about your surname. It matters to me that I get your name right.",
      "confident": "I spelled your surname incorrectly. That's my mistake.",
      "respectful": "Please accept my apology for getting your surname wrong. How would you like it written?"
    }
  },
  {
    "id": "ex083",
    "language": "en",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "Bring a spare tote if you have one.",
    "variants": {
      "witty": "If there's a tote doing nothing at home, give it a day out with us.",
      "funny": "Got a tote between jobs? Bring it along if you can.",
      "culturally inspired": "If you have a spare bag for the bazaar, bring it along.",
      "confident": "Bring a spare tote if there's one handy.",
      "custom": "Got a spare tote? Bring it if you can."
    }
  },
  {
    "id": "ex084",
    "language": "en",
    "mode": "reply",
    "relationship": "friend",
    "text": "Window open or fan on?",
    "variants": {
      "witty": "What does the room vote for? I'm open to either.",
      "funny": "Let's ask the breeze if it plans to show up today.",
      "culturally inspired": "Is there a breeze outside? That might settle the fan debate.",
      "confident": "Which would you prefer?",
      "custom": "Is there a breeze?"
    }
  },
  {
    "id": "ex085",
    "language": "en",
    "mode": "express",
    "relationship": "friend",
    "text": "Remind everyone to label their lunchboxes",
    "variants": {
      "witty": "Give your lunchbox a name tag so we can skip the identity parade.",
      "funny": "Lunchboxes can't introduce themselves. Names on the lids, please!",
      "culturally inspired": "Please put your name on your tiffin box before they all end up on one shelf.",
      "confident": "Please put your name on your lunchbox.",
      "custom": "Names on lunchboxes, please."
    }
  },
  {
    "id": "ex086",
    "language": "en",
    "mode": "rewrite",
    "relationship": "stranger",
    "text": "I prefer you ask before adding me to the group.",
    "variants": {
      "professional": "Please check with me before adding me to the group.",
      "respectful": "I'd appreciate it if you asked before adding me to the group.",
      "confident": "I want to decide whether to join. Please ask me first.",
      "mature": "I'm happy to consider an invitation. Please let me choose before you add me.",
      "subtle": "Could you send me an invite first?"
    }
  },
  {
    "id": "ex087",
    "language": "en",
    "mode": "reply",
    "relationship": "teacher",
    "text": "Can you explain which deadline you are referring to?",
    "variants": {
      "professional": "Could we check the assignment title together so I can confirm the deadline I mean?",
      "respectful": "May I show you the assignment I'm asking about?",
      "confident": "I'd like to identify the assignment first so we discuss the correct deadline.",
      "mature": "I may be mixing up the dates. Could I show you the assignment I mean?",
      "subtle": "Could I point it out in the assignment list?"
    }
  },
  {
    "id": "ex088",
    "language": "en",
    "mode": "express",
    "relationship": "colleague",
    "text": "Ask what time zone the calendar invite uses",
    "variants": {
      "professional": "Could you confirm the time zone on the calendar invitation?",
      "respectful": "Would you mind checking which time zone the invitation uses?",
      "confident": "I need the time zone to read the invite correctly. Which one is it?",
      "mature": "I'd like to avoid a mix-up. Which time zone is the invitation in?",
      "subtle": "Which time zone is on the invite?"
    }
  },
  {
    "id": "ex089",
    "language": "en",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "It's okay to leave the get-together early if you're tired.",
    "variants": {
      "comforting": "If you're tired, head home when you need to. It's okay to leave early.",
      "emotional": "I'd rather you got some rest than stayed just to keep up with everyone.",
      "mature": "Come for as long as feels comfortable. Leaving early is fine.",
      "subtle": "Tired? You can call it an early night.",
      "respectful": "You know your energy best. Leave whenever you need."
    }
  },
  {
    "id": "ex090",
    "language": "en",
    "mode": "reply",
    "relationship": "friend",
    "text": "I feel silly for being homesick on the first night.",
    "variants": {
      "comforting": "First night and everything's unfamiliar. Missing home makes sense.",
      "emotional": "Little things from home can feel very far away on a first night. What do you miss?",
      "mature": "There's no right time to start missing home. Want to talk for a bit?",
      "subtle": "What's one thing from home you're missing tonight?",
      "respectful": "You don't have to explain away how you feel. I'm happy to listen."
    }
  },
  {
    "id": "ex091",
    "language": "en",
    "mode": "express",
    "relationship": "family",
    "text": "Give my cousin space after an argument",
    "variants": {
      "comforting": "Take the time you need after our argument. We can talk when you're ready.",
      "emotional": "I care about you, and I can give you a little quiet after that argument.",
      "mature": "We can step back for now. Let me know when you want to revisit this.",
      "subtle": "I'll give you some space. We can talk later.",
      "respectful": "I'll respect your space and wait for you to bring it up."
    }
  },
  {
    "id": "ex092",
    "language": "en",
    "mode": "rewrite",
    "relationship": "partner",
    "text": "I like making ordinary plans with you.",
    "variants": {
      "flirty": "Even running an errand sounds suspiciously like a date when it's with you.",
      "romantic": "Making little everyday plans with you is one of my favourite things.",
      "poetic": "An ordinary day feels like something to look forward to when we plan it together.",
      "bold": "I love making everyday plans with you. I want more of that.",
      "subtle": "It's nice having someone to make small plans with. Especially you."
    }
  },
  {
    "id": "ex093",
    "language": "en",
    "mode": "reply",
    "relationship": "crush",
    "text": "Do you want the seat beside me?",
    "variants": {
      "flirty": "Beside you? That's a fairly persuasive seat upgrade.",
      "romantic": "I'd love to sit beside you, if it's still free.",
      "poetic": "Leave a little room by your side. That's where I'd like to sit.",
      "bold": "Yes, I'd like the seat beside you.",
      "subtle": "If it's free, I'd like that."
    }
  },
  {
    "id": "ex094",
    "language": "en",
    "mode": "express",
    "relationship": "partner",
    "text": "Tell them their little voice notes make me smile",
    "variants": {
      "flirty": "Your voice notes have a habit of catching me smiling at my phone.",
      "romantic": "Hearing your voice in a little note always makes me smile.",
      "poetic": "Your voice notes leave a small bright spot in my day.",
      "bold": "Your voice notes make me smile. I wanted you to know.",
      "subtle": "I smile a little when one of your voice notes comes in."
    }
  },
  {
    "id": "ex095",
    "language": "en",
    "mode": "rewrite",
    "relationship": "friend",
    "text": "We've taken more breaks than practice rounds.",
    "variants": {
      "funny": "The breaks are getting plenty of practice today.",
      "witty": "Our best-rehearsed move is calling a break.",
      "sarcastic": "Impressive training session. Our rest intervals look ready for competition.",
      "savage": "If breaks counted as rounds, we'd already be champions.",
      "culturally inspired": "Even a cha stall takes fewer tea breaks than this practice group."
    }
  },
  {
    "id": "ex096",
    "language": "en",
    "mode": "reply",
    "relationship": "friend",
    "text": "I can totally fit one more plant on this shelf.",
    "variants": {
      "funny": "Does the shelf get a vote in this decision?",
      "witty": "Your definition of space is doing some heavy lifting.",
      "sarcastic": "Of course. The shelf looked positively lonely under all those leaves.",
      "savage": "That shelf has applied for its own gardening assistant.",
      "culturally inspired": "One more tub on there and the shelf can open a little nursery."
    }
  },
  {
    "id": "ex097",
    "language": "en",
    "mode": "express",
    "relationship": "friend",
    "text": "Joke about a shopping list longer than our evening walk",
    "variants": {
      "funny": "The shopping list may need its own pair of walking shoes.",
      "witty": "The list is long enough to count as our route.",
      "sarcastic": "A quick shop, naturally. We'll finish just in time for next week's walk.",
      "savage": "Our evening walk just got outdistanced by a piece of paper.",
      "culturally inspired": "Read that bazaar list out loud and we'll have done half our walk."
    }
  },
  {
    "id": "ex098",
    "language": "en",
    "mode": "rewrite",
    "relationship": "colleague",
    "text": "I'm sorry I interrupted your presentation.",
    "variants": {
      "apologetic": "I'm sorry I cut in during your presentation.",
      "mature": "I should have waited for you to finish presenting before speaking.",
      "emotional": "I feel bad about breaking your flow during the presentation. I'm sorry.",
      "confident": "I interrupted your presentation. That was my mistake.",
      "respectful": "I should have let you finish. Please accept my apology for interrupting."
    }
  },
  {
    "id": "ex099",
    "language": "en",
    "mode": "reply",
    "relationship": "friend",
    "text": "I wanted to tell them the news myself.",
    "variants": {
      "apologetic": "I'm sorry. It was your news to share.",
      "mature": "I should have left that choice to you. Do you want to talk about it?",
      "emotional": "I feel awful that I took that moment away from you. I'm sorry.",
      "confident": "I spoke out of turn. The news was yours to tell.",
      "respectful": "You should have been able to share it in your own time. I'm sorry."
    }
  },
  {
    "id": "ex100",
    "language": "en",
    "mode": "express",
    "relationship": "friend",
    "text": "I returned the borrowed book with a folded cover",
    "variants": {
      "apologetic": "I'm sorry I returned your book with the cover folded.",
      "mature": "Your book came back with a folded cover. I should have looked after it better.",
      "emotional": "I feel bad seeing that fold on the book you lent me. I'm sorry.",
      "confident": "The cover got folded while I had your book. That's on me.",
      "respectful": "I'm sorry about the folded cover. How would you like me to make it right?"
    }
  }
];
