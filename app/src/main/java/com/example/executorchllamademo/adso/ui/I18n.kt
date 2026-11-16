package com.example.executorchllamademo.adso.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Menu translations. Hindi covers the whole interface (Llama 3.2 also answers in Hindi).
 * The other Indian languages cover the main navigation and buttons; anything not listed stays in English.
 * Translations should be checked by native speakers before release.
 */
val LocalLang = staticCompositionLocalOf { "en" }

@Composable
fun tr(en: String): String {
    val lang = LocalLang.current
    if (lang == "en") return en
    return (if (lang == "hi") HI[en] else CORE[lang]?.get(en)) ?: en
}

private val HI = mapOf(
    "Home" to "होम", "Plan" to "योजना", "Progress" to "प्रगति", "Settings" to "सेटिंग्स",
    "Get started" to "शुरू करें", "Continue" to "जारी रखें", "Next" to "अगला", "Check" to "जाँचें", "Start" to "शुरू करें",
    "Back" to "वापस",
    "What are you aiming for?" to "आपका लक्ष्य क्या है?",
    "Your name" to "आपका नाम", "Where are you studying?" to "आप कहाँ पढ़ रहे हैं?",
    "Your goal" to "आपका लक्ष्य", "In your words" to "अपने शब्दों में", "Target date" to "लक्ष्य तारीख",
    "Time each day" to "रोज़ का समय", "Topics you like" to "पसंदीदा विषय",
    "What are you studying?" to "आप क्या पढ़ रहे हैं?", "Courses" to "कोर्स", "Add a course" to "कोर्स जोड़ें",
    "See my gaps" to "मेरी कमियाँ देखें", "Here’s where you stand" to "आप यहाँ हैं",
    "Make my learning pathway" to "मेरा सीखने का रास्ता बनाएँ",
    "What should we study today?" to "आज हम क्या पढ़ें?", "Ask AdSo anything…" to "AdSo से कुछ भी पूछें…",
    "Today’s focus" to "आज का फ़ोकस", "Your learning pathway" to "आपका सीखने का रास्ता", "Coming up" to "आगे",
    "Study plan" to "अध्ययन योजना", "This week" to "इस हफ़्ते", "Learning route" to "सीखने का क्रम",
    "Rebuild plan" to "योजना फिर से बनाएँ", "Plan updated" to "योजना बदली गई",
    "Your gaps" to "आपकी कमियाँ", "Skill map" to "कौशल नक्शा", "Study time this week" to "इस हफ़्ते पढ़ाई का समय",
    "Study time" to "पढ़ाई का समय", "Sessions" to "सत्र", "Quiz accuracy" to "क्विज़ सटीकता",
    "Model" to "मॉडल", "Language" to "भाषा", "Appearance" to "रूप",
    "Next question" to "अगला प्रश्न", "See result" to "नतीजा देखें", "Correct" to "सही", "Not quite" to "कुछ गलत",
    "Nice work!" to "बहुत बढ़िया!", "Good effort" to "अच्छी कोशिश", "See updated plan" to "नई योजना देखें",
    "Writing your next question…" to "अगला प्रश्न लिखा जा रहा है…",
    "Chats" to "चैट", "New chat" to "नई चैट", "Choose a model" to "मॉडल चुनें", "Ask AdSo" to "AdSo से पूछें",
    "Message" to "संदेश", "Send" to "भेजें", "Stop" to "रोकें",
    "Practice with a quiz" to "क्विज़ से अभ्यास", "Done" to "हो गया", "Now" to "अभी",
)

private fun core(home: String, plan: String, progress: String, settings: String, start: String, cont: String, check: String, ask: String) = mapOf(
    "Home" to home, "Plan" to plan, "Progress" to progress, "Settings" to settings,
    "Get started" to start, "Start" to start, "Continue" to cont, "Check" to check, "Ask AdSo" to ask,
)

private val CORE = mapOf(
    "bn" to core("হোম", "পরিকল্পনা", "অগ্রগতি", "সেটিংস", "শুরু করুন", "চালিয়ে যান", "যাচাই করুন", "AdSo-কে জিজ্ঞাসা"),
    "te" to core("హోమ్", "ప్రణాళిక", "పురోగతి", "సెట్టింగ్‌లు", "ప్రారంభించండి", "కొనసాగించండి", "తనిఖీ చేయండి", "AdSoని అడగండి"),
    "mr" to core("मुख्यपृष्ठ", "योजना", "प्रगती", "सेटिंग्ज", "सुरू करा", "पुढे चला", "तपासा", "AdSo ला विचारा"),
    "ta" to core("முகப்பு", "திட்டம்", "முன்னேற்றம்", "அமைப்புகள்", "தொடங்கு", "தொடர்க", "சரிபார்", "AdSo-இடம் கேள்"),
    "gu" to core("હોમ", "યોજના", "પ્રગતિ", "સેટિંગ્સ", "શરૂ કરો", "ચાલુ રાખો", "તપાસો", "AdSo ને પૂછો"),
    "kn" to core("ಮುಖಪುಟ", "ಯೋಜನೆ", "ಪ್ರಗತಿ", "ಸೆಟ್ಟಿಂಗ್‌ಗಳು", "ಪ್ರಾರಂಭಿಸಿ", "ಮುಂದುವರಿಸಿ", "ಪರಿಶೀಲಿಸಿ", "AdSo ಕೇಳಿ"),
    "ml" to core("ഹോം", "പദ്ധതി", "പുരോഗതി", "ക്രമീകരണങ്ങൾ", "ആരംഭിക്കുക", "തുടരുക", "പരിശോധിക്കുക", "AdSo-യോട് ചോദിക്കൂ"),
    "or" to core("ମୂଳପୃଷ୍ଠା", "ଯୋଜନା", "ପ୍ରଗତି", "ସେଟିଂସ୍", "ଆରମ୍ଭ କରନ୍ତୁ", "ଜାରି ରଖନ୍ତୁ", "ଯାଞ୍ଚ କରନ୍ତୁ", "AdSo କୁ ପଚାରନ୍ତୁ"),
    "pa" to core("ਹੋਮ", "ਯੋਜਨਾ", "ਤਰੱਕੀ", "ਸੈਟਿੰਗਾਂ", "ਸ਼ੁਰੂ ਕਰੋ", "ਜਾਰੀ ਰੱਖੋ", "ਜਾਂਚੋ", "AdSo ਨੂੰ ਪੁੱਛੋ"),
    "as" to core("হোম", "পৰিকল্পনা", "অগ্ৰগতি", "ছেটিংছ", "আৰম্ভ কৰক", "আগবাঢ়ক", "পৰীক্ষা কৰক", "AdSoক সোধক"),
    "ur" to core("ہوم", "منصوبہ", "پیش رفت", "ترتیبات", "شروع کریں", "جاری رکھیں", "جانچیں", "AdSo سے پوچھیں"),
)
