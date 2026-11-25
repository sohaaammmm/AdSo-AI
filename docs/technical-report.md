# AdSo AI: technical report

An offline AI learning companion for Android. It builds personalised learning pathways from a learner's
goals, interests and current skill level, and keeps adapting them as the learner studies. The advisory and
reasoning part is a **Llama 3.2** model running on the phone with **ExecuTorch**. After a one-time model
download, no internet connection is needed.

*Bharat AI-SoC Challenge 2026–27, Problem Statement 4. Report updated 20 Nov 2026. All numbers were measured
on the team's test phone unless stated otherwise.*

Source code: https://github.com/sohaaammmm/AdSo-AI

## 1. Problem and objectives

| Objective (brief + mentoring session) | How AdSo does it |
|---|---|
| Understand learner goals | Onboarding asks for goal type (exam prep, career, new skill, certification, tech roadmap, lifelong learning), the goal in the learner's own words, target date (2 weeks to 12 months), daily time (15 min to 6 h), interests (20 options) and study level. The learner can also state goals and constraints in the tutor chat. |
| Identify skill gaps | A short skill check per course marks every topic *Strong*, *Needs work* or *New to you*. Progress shows a "Your gaps" list and a before/now skill map. |
| Recommend learning pathways | **Llama orders the course's topics** for this learner and gives a reason for each step (JSON, parsed on the phone). |
| Generate study plans | The pathway becomes (a) a **day-by-day plan** of Learn, Practice, Quiz and Review sessions fitted to the daily time, and (b) a **week-by-week roadmap** to the target date ("Weeks 1–5 Graph theory … Week 13 Final review"). Lessons list free resources. |
| Adapt over time | After **every quiz, Llama reviews the score and the questions the learner got wrong**, writes advice, and moves the topic to study next to the front of the pathway. In chat, the learner can change the plan in plain words ("I only have 2 hours a day, focus on graph theory first"). |
| Conversational guidance | "Ask AdSo" tutor chat with streaming answers, history, and answers sized to the question. |
| Progress tracking | Real study time (counted while the app is on screen), sessions, quiz accuracy, an interactive weekly graph, skill map. |
| Indian languages | Hindi: whole interface + AI answers. Tamil, Telugu, Bengali, Marathi: AI answers (beta). The chat replies in the language the question is written in. 11 languages for the main menus. |

## 2. Model

| | Value |
|---|---|
| Model (default) | Meta **Llama 3.2 1B Instruct, QAT+LoRA** (quantization-aware training with LoRA adaptors) |
| Parameters | ≈ 1.24 B |
| Quantization | **INT4 weights**, 8-bit dynamic activations ("INT4_EO8", Meta's official mobile format) |
| File | `Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte`, **1.18 GB** (SHA-256 checked) |
| Source | ExecuTorch community build on Hugging Face (`executorch-community/Llama-3.2-1B-Instruct-QLORA_INT4_EO8-ET`) |
| Also supported | Llama 3.2 1B Instruct SpinQuant INT4 (1.14 GB) |
| Context window | 2,048 tokens |
| Tokenizer | Llama 3 tiktoken `tokenizer.model` (same file for both 1B builds) |
| Sampling temperature | **0.70** default, adjustable 0.70–0.80 (Settings → Advanced) |

**Model choice.** We evaluated three Llama 3.2 builds on the phone:

| | 3B SpinQuant INT4 | 1B SpinQuant INT4 | 1B QAT+LoRA INT4 |
|---|---|---|---|
| File size | 2.55 GB | 1.14 GB | 1.18 GB |
| Load time | 21.1 s | 6.7 s | 7.0 s |
| Generation | 2.1 tokens/s | 8.2 tokens/s | 7.9 tokens/s |
| Chat answer, "Explain a linked list with a simple example" | 150 s | 43 s | – |

3B gave somewhat better text but was about 4× slower and used 2.5 GB of storage, so it was **removed**.
Between the two 1B builds the speed is the same; Meta reports QAT+LoRA as the more accurate of its quantized
1B models, so it is the default. Llama 3 8B was ruled out: it needs about 5.5–6 GB of RAM in INT4 (the phone has
about 2.5 GB free) and about 32 GB of RAM to export.

## 3. Arm hardware, runtime and measurements

Test phone: **TECNO POVA 5 Pro 5G** (TECNO LH8n), MediaTek **Dimensity 6080**: 2× Arm Cortex-A76 at 2.4 GHz +
6× Cortex-A55 at 2.0 GHz (arm64-v8a), Mali GPU, 8 GB RAM. Tests in airplane mode.

**Runtime.** ExecuTorch 1.1.0 Android library (`org.pytorch:executorch-android`) through its Java/JNI
`LlmModule` API, with the **XNNPACK** CPU backend. The library includes Arm **KleidiAI** micro-kernels (we found
the `kai_*` INT4/INT8 matmul kernels in `libexecutorch.so`), which use Arm NEON / dot-product instructions on
these cores. ExecuTorch runs inference on the performance cores it detects.

| Measurement (Llama 3.2 1B QAT+LoRA unless noted) | Result |
|---|---|
| Model load | **7.0 s** |
| Prompt processing (prefill) | **29 tokens/s** |
| Generation (decode) | **7.9 tokens/s** (SpinQuant 1B: 8.2) |
| Advice after a quiz (≈ 30 tokens) | **3.5 s** |
| Learning pathway, 5 topics (SpinQuant 1B) | 31 s; the plan is shown **instantly** from the skill check and Llama refines it in the background |
| Course design for a new subject (5 topics) | 31 s |
| Lesson (≈ 150 words) | 26 s |
| Memory: model native heap (PSS) | **≈ 1.62 GB** |
| Memory: whole app (PSS, incl. UI and the orb's WebView) | ≈ 1.92 GB (ExecuTorch reports RSS 2.1 GB right after loading) |
| Offline | 100 % after the one-time model download |
| APK size (release, arm64 only) | **20 MB** (debug build: 133 MB) |

Generation speed comes from ExecuTorch's own stats (`generated_tokens` / (`inference_end_ms` − `prompt_eval_end_ms`))
and is shown under every chat answer.

**Against the mentors' targets.**
- *≥ 22 tokens/s:* not reached on this phone (≈ 8 tokens/s). The Dimensity 6080 is a mid-range chip with two
  Cortex-A76 cores; decoding a 1B model is limited by memory bandwidth and those two cores. Llama 3.2 1B is
  already the smallest Llama and INT4 the smallest supported weight format. What we tried:
  - *More CPU threads:* the Android `LlmModule` API has no thread setting; a workaround that resized ExecuTorch's
    shared thread pool made generation hang, so it was removed.
  - *NPU:* ExecuTorch's MediaTek NeuroPilot backend lists only the **Dimensity 9300 and 9400** as supported, and
    runs through native runners with vendor libraries pushed to the device. The Dimensity 6080 is not supported.
  - On flagship Arm phones (Cortex-X cores, and SME2 where available, which the KleidiAI kernels in this library
    already include) the same app and model run much faster; this is the realistic path to 22 tokens/s.
  - Design choices that keep AdSo responsive at 8 tokens/s: streaming output, short structured requests (pathway
    ≈ 300 tokens, advice ≈ 120), answers sized to the question, instant rule-based plans refined by Llama in the
    background, and background work stopping as soon as the learner needs the model.
- *INT4 model under 1.8 GB RAM:* the model's own memory is ≈ 1.62 GB (met); the whole app process is ≈ 1.9 GB.
- *100 % offline:* met after the model download.

## 4. Architecture

```
AdsoActivity (single activity, Jetpack Compose, hand-written navigation)
 ├─ AdsoViewModel          goals, skill check, pathway, plan, roadmap, lessons, quiz, coach, chat, progress
 ├─ adso/llm/LlamaEngine   ExecuTorch LlmModule wrapper: load, stream tokens, stop, model choice, temperature
 ├─ adso/llm/Advisor       prompts, lenient JSON parsing, fallbacks
 ├─ adso/llm/ModelDownloader  one-time model download with resume and SHA-256 check
 ├─ adso/data              Room database (profile, skills, study log, courses, chats), course content, resources
 └─ adso/ui                screens; the 3D orb (React Three Fiber in an offline WebView)
```

- **Data flow (goal intake → Llama → roadmap → adaptive loop):** the learner's goal, skills and time go into a
  prompt; Llama on the phone returns JSON (pathway, quiz question, advice, course topics); AdSo parses it and shows
  the plan; quiz results and chat requests feed back into the next Llama request.
- **One model in memory.** LlamaEngine runs on its own thread with a mutex; every request resets the context and
  uses the Llama 3 chat template; chat keeps as many recent turns as fit in 2,048 tokens.
- **Storage:** SQLite via **Room** (v5, with migrations so updates keep the learner's data).
- **Privacy:** everything stays on the phone; the profile (name, email, phone) is stored locally only.

## 5. Where Llama does the reasoning

| Feature | Prompt asks for | Output | If unusable |
|---|---|---|---|
| Learning pathway | Order all topics for this learner; a short reason each | JSON | Rule order: needs work → new → strong (shown instantly anyway) |
| Quiz coach | Advice from the score and wrong questions; which topic next | JSON | Rule-based note only |
| Plan changes in chat | Which topic to focus on / which are already known (time and dates are read by rules) | JSON | No change |
| Adaptive quiz | One MCQ at a difficulty set by the last answers, then an independent answer check | JSON + 1 letter | Built-in checked question bank |
| Lessons | Learn / Practice / Review text for the goal and interests | Streamed text | Message + go to quiz |
| Tutor chat | Answer sized to the question, in the learner's language | Streamed text | Error message |
| New course | 5 topics with goals for any subject; course-name typo fix | JSON | Ask to retry or pick a built-in course |

**Structured output at temperature 0.7.** The project requires 0.7–0.8, which makes JSON less reliable than
greedy decoding. AdSo asks for JSON only with a one-line example, extracts the first `{…}` block, salvages complete
objects from cut-off replies, matches topic ids loosely, validates every field, retries where it is cheap, and
falls back to rules or the question bank. The UI says when something was written by Llama.

Findings from testing on the phone, and the fixes:
- **Malformed JSON** from 1B → salvage step + loose id matching; pathway then written by Llama in 31 s.
- **Wrong answer keys** in 1B quiz questions → second one-letter check; checked bank questions shown instantly.
- **Slow course design** → Llama writes only 5 topics; the skill check is a self-rating.
- **Plan waited minutes for Llama** → plan shown instantly, Llama refines it in the background.
- **Advice and chosen topic disagreed** → the topic named in the advice wins.
- **Course-name typos** ("Tf antenna") → Llama suggests the standard name, accepted only if it is a small edit;
  the learner can rename a course.

## 6. Features

- Onboarding: language, name, level, goal, target date, daily time, interests → courses → skill check.
- 8 built-in courses (Data Structures, Discrete Mathematics, Python, Operating Systems, Database Systems,
  Computer Networks, Machine Learning Basics, Calculus) plus any course the learner names (designed by Llama);
  courses can be renamed and deleted.
- Home: the orb, an Ask box, today's focus, the learning pathway (% done), "For your interests" examples, what's next.
- Plan: this week (sessions by day) and the roadmap (weeks to the target date, Llama's order and reasons).
- Lessons with free resources, adaptive quiz, result with Llama's advice and the plan change.
- Progress: real study time, sessions, quiz accuracy, interactive weekly graph, gaps, skill map.
- Ask AdSo chat (monochrome): streaming, stop, history, plan changes, replies in the learner's language.
- Profile (top-left icon): name, email, phone, study level, links to goals and courses.
- Settings: AI on this phone (model choice), Advanced (temperature, reload, speed, logs), appearance, 13 languages.

## 7. Limitations and future work

- Speed: ≈ 8 tokens/s on a mid-range chip; 22 tokens/s needs a flagship Arm phone or NPU support for more chips.
- Small models make factual slips; AdSo labels AI-written content. Retrieval from course notes would help.
- Tamil, Telugu, Bengali and Marathi answers are beta: Llama 3.2 officially supports Hindi but not these.
  Menu translations other than Hindi need checking by native speakers.
- Sign-in with Google or phone OTP is not included: it needs a cloud project (Google Cloud / Firebase) and
  internet, which goes against the offline, private design; it could be added as an optional sync later.
- The release APK is signed with a local test key; a Play Store release needs a real upload key.

## 8. How to build and run

1. Install the release APK and tap **Download** on "Get AdSo's AI" (1.18 GB, once), **or** push the model with adb:
   `adb push Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte tokenizer.model /data/local/tmp/llama/`
2. Build: `./gradlew assembleRelease` (or `assembleDebug`). On a low-memory Windows PC add
   `-Pkotlin.compiler.execution.strategy=in-process`.
3. The 3D orb: `npm install` and `npm run build` in `orb/` (the output is already in `app/src/main/assets/orb`).
