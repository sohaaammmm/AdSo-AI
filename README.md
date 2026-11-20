# AdSo AI

An offline AI learning companion for Android. AdSo builds a personalised learning pathway from your goals,
interests and current skill level, and keeps adapting it as you study. All the reasoning is done by a
**Llama 3.2** model running on the phone with **ExecuTorch**. No internet is needed after setup.

Built for the Bharat AI-SoC Challenge 2026–27, Problem Statement 4 (personalised AI learning companion).

## What it does

| The brief asks for | How AdSo does it |
|---|---|
| Understand learner goals | Onboarding: goal type (exam prep, career, new skill, certification, tech roadmap, lifelong learning), the goal in your own words, target date (2 weeks to 12 months), daily time (15 min to 6 h), interests |
| Identify skill gaps | A short skill check per course marks each topic *Strong*, *Needs work* or *New*; Progress keeps a "Your gaps" list and a skill map |
| Recommend learning pathways | Llama orders the course topics for you and gives a reason for each (JSON, parsed on the phone) |
| Generate study plans | The pathway becomes a day-by-day plan of Learn, Practice, Quiz and Review sessions fitted to your time and date |
| Adapt over time | After every quiz, Llama reviews your score and the questions you missed, gives advice and moves the topic you should study next to the front of the pathway |

Also: 8 built-in courses plus any course you name (Llama designs it), lessons written by Llama, adaptive quizzes,
an "Ask AdSo" tutor chat with history, "For your interests" examples on Home, a real study-time graph
(counted while the app is open), and Hindi for the whole app and the AI's answers (main menus in 11 more Indian languages).

## Model and runtime

| | |
|---|---|
| Default model | Llama 3.2 1B Instruct, **QAT+LoRA**, INT4 weights / 8-bit dynamic activations (≈ 1.24 B parameters, 1.18 GB) |
| Also supported | Llama 3.2 1B Instruct SpinQuant INT4 (1.14 GB) |
| Runtime | ExecuTorch 1.1.0 Android library, XNNPACK CPU backend (Arm) |
| Context | 2,048 tokens |
| Temperature | 0.70–0.80 (Settings → Advanced) |

Measured on a TECNO POVA 5 Pro 5G (MediaTek Dimensity 6080, 2× Cortex-A76 + 6× Cortex-A55, 8 GB RAM), airplane mode:

| | 1B QAT+LoRA | 1B SpinQuant |
|---|---|---|
| Model load | 7.0 s | 6.7 s |
| Generation | 7.9 tokens/s | 8.2 tokens/s |
| Prompt processing | 29 tokens/s | – |
| Advice after a quiz | 3.5 s | – |
| Learning pathway (5 topics) | – | 31 s (the plan shows instantly; Llama refines it in the background) |

More detail, findings and limitations: [docs/technical-report.md](docs/technical-report.md).

## Run it

1. Get the model and tokenizer (public, from the ExecuTorch community on Hugging Face):
   `Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte` and `tokenizer.model` from
   https://huggingface.co/executorch-community/Llama-3.2-1B-Instruct-QLORA_INT4_EO8-ET
2. Copy them to the phone (USB debugging on):
   ```bash
   adb shell mkdir -p /data/local/tmp/llama
   adb push Llama-3.2-1B-Instruct-QLORA_INT4_EO8.pte tokenizer.model /data/local/tmp/llama/
   ```
3. Build and install (JDK 17 or 21):
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Open **AdSo AI**. The model loads by itself when the app starts.

## How it is built

```
AdsoActivity (single activity, Jetpack Compose)
 ├─ AdsoViewModel         goals, skill check, pathway, plan, lessons, quiz, coach, chat, progress
 ├─ adso/llm/LlamaEngine  ExecuTorch LlmModule wrapper: load, stream, stop, model choice, temperature
 ├─ adso/llm/Advisor      prompts, lenient JSON parsing, fallbacks
 ├─ adso/data             Room (SQLite): profile, skills, study log, courses, chats
 └─ adso/ui               screens; the animated orb (React Three Fiber in an offline WebView, source in orb/)
```

Small models at temperature 0.7 often write slightly broken JSON, so AdSo extracts and salvages JSON, matches
topic ids loosely, checks quiz answers with a second request, and falls back to rules or a built-in question
bank when a reply can't be used. The UI always says whether something was written by Llama.

## Credits and license

Based on the ExecuTorch Llama Android demo from
[meta-pytorch/executorch-examples](https://github.com/meta-pytorch/executorch-examples) (its original README is in
[docs/ExecuTorch-LlamaDemo-README.md](docs/ExecuTorch-LlamaDemo-README.md)). BSD license, see [LICENSE](LICENSE).
Llama 3.2 is licensed by Meta under the Llama 3.2 Community License. Inter font: SIL Open Font License.
