use std::{hint::black_box, path::Path, time::Instant};

use riti::{config::Config, context::RitiContext, keycodes::*, suggestion::Suggestion};

const TIMED: [&str; 4] = ["spondon", "shilpo", "krri", "montro"];
const FIXTURES: [(&str, &str); 25] = [
    ("Ami", "আমি"), ("tomake", "তোমাকে"), ("bhalobashi", "ভালোবাসি"),
    ("ami", "আমি"), ("Bangladesh", "বাংলাদেশ"), ("krri", "কৃ"),
    ("kkhe", "ক্ষে"), ("ou", "ঔ"), ("chaa", "চা"), ("strii", "স্ত্রী"),
    ("bhaat", "ভাত"), ("123", "১২৩"), ("spondon", "স্পন্দন"),
    ("shobdo", "শব্দ"), ("groho", "গ্রহ"), ("shilpo", "শিল্প"), ("montro", "মন্ত্র"),
    ("kSh", "ক্ষ"), ("bry", "ব্র্য"), ("Ng", "ঙ"), ("ShT", "ষ্ট"),
    ("bhalobasi", "ভালোবাসি"), ("valobashi", "ভালোবাসি"), ("valo", "ভালো"),
    ("kamon", "কেমন"),
];

fn make_config(dictionary: bool) -> Config {
    let mut config = Config::default();
    assert!(config.set_layout_file_path("avro_phonetic"));
    assert!(config.set_database_dir(Path::new(env!("CARGO_MANIFEST_DIR")).join("data").to_str().unwrap()));
    assert!(config.set_user_dir("/tmp/kotha-riti-eval-user"));
    config.set_phonetic_suggestion(dictionary);
    config
}

fn keycode(ch: u8) -> u16 {
    match ch {
        b'a'..=b'z' => VC_A + u16::from(ch - b'a'),
        b'A'..=b'Z' => VC_A_SHIFT + u16::from(ch - b'A'),
        b'1'..=b'9' => VC_1 + u16::from(ch - b'1'),
        b'0' => VC_0,
        _ => panic!("unsupported benchmark character {ch}"),
    }
}

fn type_word(ctx: &RitiContext, word: &str) -> Suggestion {
    ctx.finish_input_session();
    let mut suggestion = Suggestion::empty();
    for ch in word.bytes() {
        suggestion = ctx.get_suggestion_for_key(keycode(ch), 0, 0);
    }
    suggestion
}

fn elapsed(ctx: &RitiContext, passes: usize) -> f64 {
    let now = Instant::now();
    for _ in 0..passes {
        for word in TIMED {
            black_box(type_word(ctx, word));
        }
    }
    now.elapsed().as_secs_f64() * 1000.0
}

fn timing(dictionary: bool) {
    let config = make_config(dictionary);
    let mut init = vec![];
    let mut cold = vec![];
    let mut warm = vec![];
    for _ in 0..7 {
        let now = Instant::now();
        let ctx = RitiContext::new_with_config(&config);
        init.push(now.elapsed().as_secs_f64() * 1000.0);
        cold.push(elapsed(&ctx, 1));
        warm.push(elapsed(&ctx, 50));
    }
    init.sort_by(f64::total_cmp);
    cold.sort_by(f64::total_cmp);
    warm.sort_by(f64::total_cmp);
    println!("dictionary={dictionary} medians ms init={:.3} first_pass_23_events={:.3} warm_1150_events={:.3}", init[3], cold[3], warm[3]);
}

fn main() {
    timing(true);
    timing(false);

    let mut top1 = 0;
    let mut top3 = 0;
    let full_ctx = RitiContext::new_with_config(&make_config(true));
    let plain_ctx = RitiContext::new_with_config(&make_config(false));

    for (input, expected) in FIXTURES {
        let full = type_word(&full_ctx, input);
        let plain = type_word(&plain_ctx, input);
        let all = full.get_suggestions();
        let rank = all.iter().position(|word| word == expected).map(|n| n + 1);
        top1 += usize::from(rank == Some(1));
        top3 += usize::from(rank.is_some_and(|n| n <= 3));
        println!("{}	{}	{}	{:?}	{:?}	{}	{}", input, expected, plain.get_lonely_suggestion(), &all[..all.len().min(4)], rank, full.previously_selected_index() + 1, all.len());
    }
    println!("Top 1 {top1}/{} and top 3 {top3}/{}", FIXTURES.len(), FIXTURES.len());
    let _ = type_word(&full_ctx, "spondon");
    let after = full_ctx.backspace_event(false);
    assert_eq!(after.get_auxiliary_text(), "spondo");
    full_ctx.finish_input_session();
    assert!(!full_ctx.ongoing_input_session());
    println!("Backspace restored spondo; finishing cleared the composing session");
}
