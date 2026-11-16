# AdSo AI: technical report

An offline AI learning companion for Android. It builds personalised learning pathways from a learner's
goals, interests and current skill level, using a Llama-family model that runs on the phone with
ExecuTorch. No internet connection is needed after install.

*Draft, 16 Nov 2026. Numbers marked "measured" come from the team's test phone; fill in anything marked TODO.*

## 1. Problem and objectives

The brief asks for an assistant that can:

| Objective | How AdSo does it |
|---|---|
| Understand learner goals | Onboarding asks for goal type (exam prep, career, new skill, certification, tech roadmap, lifelong learning), the goal in the learner's own words, a target date, daily study time, interests and study level. |
| Identify skill gaps | A 5-question skill check per course (each with a "Not sure yet" option) marks every topic *Strong*, *Needs work* or *New to you*. The Progress tab keeps a "Your gaps" list and a before/now skill map. |
| Recommend learning pathways | **Llama orders the course's topics** for this learner and writes a reason and a concrete goal for each step (JSON, parsed on the phone). |
| Generate study plans | The pathway is turned into a day-by-day plan (Learn, Practice, Quiz, Review sessions) that fits the learner's daily minutes and target date. |
| Adapt over time | Every quiz updates the topic's level. Passing finishes the topic and the next gap moves to the front; failing adds more practice. The plan shows a "Plan updated" note explaining each change. The learner can ask Llama to rebuild the pathway at any time. |

## 2. Model

| | Value |
|---|---|
| Model | Meta **Llama 3.2 1B Instruct** (default) and **Llama 3.2 3B Instruct** (optional, better answers, slower) |
| Parameters | ≈ 1.24 B (3B model: ≈ 3.21 B) |
| Quantization | **INT4** weights, 8-bit dynamic activations (SpinQuant "INT4_EO8", Meta's official mobile format) |
| File | `Llama-3.2-3B-Instruct-SpinQuant_INT4_EO8-2048.pte`, **2.55 GB** (1B: 1.14 GB). SHA-256 checked after download and on the phone. |
| Source | Meta's SpinQuant checkpoint exported for ExecuTorch (MediaTek Research build on Hugging Face) |
| Context window | 2,048 tokens (fixed in the .pte; larger windows need more memory than this phone has free) |
| Tokenizer | Llama 3 tiktoken `tokenizer.model` (shared by 1B and 3B) |
| Runtime | ExecuTorch 1.1.0 Android library (`org.pytorch:executorch-android`), XNNPACK CPU backend |
| Sampling temperature | **0.70** (adjustable in Settings, limited to 0.70–0.80 as required) |

**Why 3B and not 8B.** The mentor's brief mentioned Llama 3 8B ("38D"). The test phone has 7.8 GB RAM with
about 2.5 GB free in normal use. Llama 3 8B in INT4 needs about 5.5–6 GB while running, and INT8 about 9 GB,
so Android would kill the app. Exporting 8B also needs around 32 GB of PC RAM (the team's PC has 16 GB).
Llama 3.2 3B in INT4 fits and still meets "a model from the Llama family". 1B is kept as a faster option.

## 3. Hardware and measurements

Test phone: **TECNO POVA 5 Pro 5G** (TECNO LH8n), MediaTek Dimensity 6080 (Arm Cortex-A76 + A55, arm64),
7.8 GB RAM, Android, in airplane mode during tests.

| Measurement (measured on the phone) | Llama 3.2 3B INT4 | Llama 3.2 1B INT4 |
|---|---|---|
| Model load time | **21.1 s** | **6.7 s** (1.4 s in an earlier run with more free memory) |
| Generation speed | **2.1 tokens/s** | **8.2 tokens/s** |
| Learning pathway (5 topics, JSON) | TODO | TODO |
| One adaptive quiz question | ≈ 49 s (first question, model already loaded) | TODO |
| Lesson (≈ 150 words) | ≈ 40 s | TODO |
| Chat answer, "Explain a linked list with a simple example" | **150 s** (≈ 300 words) | **43 s** |
| App memory while running (PSS) | TODO (`adb shell dumpsys meminfo com.example.adsoai`) | TODO |

How to read the numbers: generation speed is reported by ExecuTorch (`generated_tokens` /
(`inference_end_ms` − `prompt_eval_end_ms`)) and shown in Settings and under each chat answer.

Observations:
- 3B gives better plans and explanations; 1B is about 4× faster. On this mid-range phone with ~2.5 GB free,
  3B is too slow for chat, so **Llama 3.2 1B is the default**. 3B can be chosen in Settings or from the chat
  header when answer quality matters more than speed.
- Speed drops when the phone is short on free memory (other apps open) or warm.

## 4. Architecture

```
AdsoActivity (single activity, Jetpack Compose, hand-written navigation)
 ├─ AdsoViewModel        learner state, skill check, pathway, plan, lessons, quiz, chat, progress
 ├─ adso/llm/LlamaEngine ExecuTorch LlmModule wrapper: load, stream tokens, stop, model choice, temperature
 ├─ adso/llm/Advisor     prompts + lenient JSON parsing + fallbacks
 ├─ adso/data            Room database (profile, skills, study log, courses, chats), course content
 └─ adso/ui              screens; the 3D orb (React Three Fiber in an offline WebView)
```

- **One model in memory.** LlamaEngine is a single instance on its own thread with a mutex. Every request
  calls `resetContext()` and uses the Llama 3 chat template; chat keeps as many recent turns as fit in the
  2,048-token window.
- **Storage:** SQLite via **Room** (v3, with migrations so updates keep the learner's data).
- **3D orb:** a React Three Fiber component (`LlamaDemo/orb`) built to one offline JS file and shown in a
  transparent WebView. It shows the AI's state: idle, thinking, generating, resting.

## 5. Where Llama does the reasoning

| Feature | Prompt asks for | Output | Fallback if unusable |
|---|---|---|---|
| Learning pathway | Order all topics for this learner; a reason and a goal per topic | JSON | Rule order: needs work → new → strong |
| Adaptive quiz | One MCQ on the topic at a difficulty chosen from the learner's last answers | JSON | Built-in question bank |
| Lessons | Learn / Practice / Review text tailored to goal and interests | Streamed text | Message + go to quiz |
| Ask AdSo chat | Tutor answer that knows the learner's goal, course and gaps | Streamed text | Error message |
| New course | 5 topics + 5 skill-check questions for any subject the learner types | JSON | Ask to retry or pick a built-in course |

**Structured output at temperature 0.7.** The project requires sampling at 0.7–0.8, which makes JSON less
reliable than greedy decoding. AdSo handles this by: asking for JSON only with a one-line example; extracting
the first `{…}` block (ignoring extra text or code fences); validating every field (ids, 4 distinct options,
answer index); retrying once; and falling back to rules or the question bank, with the UI saying which was
used ("Written by Llama" / "Question bank"). In testing, most replies parsed; one of three quiz questions
fell back to the bank after two unusable replies.

## 6. Features

- Onboarding in 3 steps: language, name, level, goal, target date, daily time, interests → courses → skill check.
- 8 built-in courses (Data Structures, Discrete Mathematics, Python, Operating Systems, Database Systems,
  Computer Networks, Machine Learning Basics, Calculus) plus **any course the learner names**, designed by Llama.
- Home: the orb, "What should we study today?", an Ask box, today's focus, the learning pathway (% done), what's next.
- Plan: this week (sessions by day) and the learning route (Llama's order, reasons, goals).
- Lessons, adaptive quiz (warm-up → easier/harder), result with the plan change explained.
- Progress: study time, sessions, quiz accuracy, interactive weekly study-time graph, gaps, skill map.
- Ask AdSo chat (monochrome): streaming answers, stop, saved chat history, new chat, model picker.
- Settings: model choice, temperature 0.70–0.80, load time and speed, appearance, 13 languages.
- **Multilingual:** Hindi translates the full interface and Llama answers in Hindi (Llama 3.2 officially
  supports Hindi). Bengali, Telugu, Marathi, Tamil, Gujarati, Kannada, Malayalam, Odia, Punjabi, Assamese and
  Urdu translate the main menus; AI answers stay in English because Llama 3.2 isn't trained for those.

## 7. Limitations and future work

- Speed: 3B at ≈ 2 tokens/s on a Dimensity 6080 is slow for long chat answers. Options: 1B for chat,
  the Vulkan GPU or MediaTek NPU backends of ExecuTorch, or shorter prompts.
- Small models make factual slips (e.g. one lesson said inserting anywhere in a linked list is O(1), which is
  only true once you hold the position). AdSo shows that content is AI-written; a review step or retrieval
  from course notes would help.
- Q&A mode: the mentor's exact intent for "Q&A should be turned on" is still being confirmed; the current
  chat answers anything about the learner's course with their goal and gaps as context.
- Menu translations other than Hindi need checking by native speakers.

## 8. How to build and run

1. Put the model and tokenizer on the phone: `adb push Llama-3.2-3B-Instruct-SpinQuant_INT4_EO8-2048.pte tokenizer.model /data/local/tmp/llama/`
2. Build: `.\gradlew.bat assembleDebug -Pkotlin.compiler.execution.strategy=in-process`
3. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
4. The 3D orb: `npm install` and `npm run build` in `LlamaDemo/orb` (output is already in `app/src/main/assets/orb`).
