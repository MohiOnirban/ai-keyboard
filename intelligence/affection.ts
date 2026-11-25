// A small, exact-intent demonstration for the product's introductory example.
// Match the complete thought so a negation, condition or named recipient cannot be lost.
const simpleThought = /^(?:i (?:really )?like you|i have a crush on you|(?:ami )?tomake (?:khub )?(?:pochondo kori|bhalo lage)|(?:amar )?tomake (?:khub )?bhalo lage|তোমাকে (?:খুব )?(?:পছন্দ করি|ভালো লাগে|ভাল লাগে)|আমার তোমাকে (?:খুব )?ভালো লাগে)[.!। ]*$/iu;
const lines: Record<string, [string, string, string]> = {
  flirty: ["তোমাকে একটু বেশিই পছন্দ করি মনে হচ্ছে। ধরা পড়ে গেলাম?", "Tomake ektu beshii pochondo kori mone hocche. Dhora pore gelam?", "I like you. Was I being at all subtle about it?"],
  romantic: ["তোমার জন্য আমার মনে আলাদা একটু জায়গা আছে। কথাটা বলতে ইচ্ছে হলো।", "Tomar jonno amar mone alada ektu jayga ache. Kothata bolte ichcha holo.", "There's a little corner of my thoughts that's yours. I wanted you to know."],
  funny: ["তোমাকে পছন্দ করি। কথাটা বলার মহড়াতেই আমার বারোটা বেজে গেল!", "Tomake pochondo kori. Kothata bolar mohoratei amar barota beje gelo!", "I like you. My very impressive speech seems to have ended there."],
  poetic: ["তোমার দিকে মনটা ফেরে, যেমন জানালার দিকে ফেরে মুখ।", "Tomar dike monta phere, jemon janalar dike phere mukh.", "My thoughts turn your way like a face turning to a window."],
  witty: ["ঘুরিয়ে বলার মতো কথা খুঁজছিলাম। সোজা কথাটাই সুন্দর। তোমাকে পছন্দ করি।", "Ghuriye bolar moto kotha khujchilam. Shoja kothatai shundor. Tomake pochondo kori.", "I could dress this up, but it fits just fine as it is. I like you."],
  sarcastic: ["তোমাকে আমার একটুও পছন্দ হয় না, এমন অভিনয়টা বাদই দিলাম। ভালো লাগে তোমাকে।", "Tomake ektuo pochondo hoy na, emon obhinoyta badi dilam. Bhalo lage tomake.", "Clearly I'm brilliant at playing it cool. I like you."],
  confident: ["তোমাকে আমার পছন্দ। এটা নিজের মুখেই জানাতে চাইলাম।", "Tomake amar pochondo. Eta nijer mukhei janate chailam.", "I like you, and I'd like you to hear it from me."],
  bold: ["তোমাকে পছন্দ করি। হ্যাঁ, তোমাকেই বলছি।", "Tomake pochondo kori. Haan, tomakei bolchi.", "I like you. Yes, you. I wanted to say it clearly."],
  subtle: ["তোমাকে একটু আলাদা রকম ভালো লাগে।", "Tomake ektu alada rokom bhalo lage.", "I have a bit of a soft spot for you."],
  mature: ["তোমাকে পছন্দ করি। তোমার কেমন লাগে, সেটা তোমার নিজের কথা।", "Tomake pochondo kori. Tomar kemon lage sheta tomar nijer kotha.", "I like you. I wanted to be honest about it and leave you room to feel however you feel."],
  emotional: ["তোমাকে ভালো লাগে। ছোট্ট কথাটা আমার কাছে অনেক কিছু।", "Tomake bhalo lage. Chhotto kothata amar kache onek kichu.", "I like you. Those three small words feel quite big to me."],
  respectful: ["একটা ব্যক্তিগত কথা বলতে চাই। আপনাকে আমার ভালো লাগে।", "Ekta bektigoto kotha bolte chai. Apnake amar bhalo lage.", "If I may say something personal, I'm fond of you."],
  professional: ["একটা ব্যক্তিগত কথা জানাতে চাই। আপনাকে আমার পছন্দ।", "Ekta bektigoto kotha janate chai. Apnake amar pochondo.", "I'd like to share something personal. I'm fond of you."],
  savage: ["ভাব নিয়েও লাভ নেই। তোমাকে যে পছন্দ করি, সেটা বলেই ফেলি।", "Bhab niyeo labh nei. Tomake je pochondo kori, sheta bolei feli.", "My attempt at being mysterious ends here. I like you."],
  comforting: ["তোমাকে আমার ভালো লাগে। এর উত্তর দেওয়ার কোনো চাপ নেই।", "Tomake amar bhalo lage. Er uttor dewar kono chap nei.", "I like you, and you don't have to find an answer for me."],
  apologetic: ["কথাটা বলতে একটু সংকোচ হচ্ছে। তোমাকে আমার পছন্দ।", "Kothata bolte ektu shonkoch hocche. Tomake amar pochondo.", "I feel a little shy saying this, but I like you."],
  "culturally inspired": ["তোমাকে ভালো লাগে। ঘুরিয়ে বলার জন্য আজ আর আড্ডা বসাব না।", "Tomake bhalo lage. Ghuriye bolar jonno aj ar adda boshabo na.", "I like you. That's my whole adda for today, in one line."],
  custom: ["তোমাকে পছন্দ করি। জানাতে ইচ্ছে হলো।", "Tomake pochondo kori. Janate ichcha holo.", "I like you. I wanted to tell you."],
};
export function affectionDemo(text: string, language: "bn" | "banglish" | "en", style?: string, intensity?: string): string | undefined {
  if (!simpleThought.test(text)) return;
  const chosen = style ?? (intensity === "bold" ? "bold" : intensity === "subtle" ? "subtle" : "custom");
  return lines[chosen]?.[language === "bn" ? 0 : language === "banglish" ? 1 : 2];
}
