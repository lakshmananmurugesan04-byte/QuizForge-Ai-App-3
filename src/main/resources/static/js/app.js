/* ====================================================================
   QuizForge AI — Client Application Logic
   ==================================================================== */

/* ---------------- Tiny API helper ---------------- */
async function apiFetch(path, options = {}) {
  const url = `${API_BASE_URL}${path}`;
  let response;
  try {
    response = await fetch(url, {
      headers: options.body instanceof FormData ? undefined : { "Content-Type": "application/json" },
      ...options,
    });
  } catch (err) {
    throw new Error(
      "Couldn't reach the QuizForge server. Check your connection or verify that the backend is running."
    );
  }

  let data = null;
  try {
    data = await response.json();
  } catch (err) {
    // Non-JSON response
  }

  if (!response.ok && !data) {
    throw new Error(`Server error (${response.status}). Please try again.`);
  }
  return data;
}

/* ---------------- App State ---------------- */
let settings = { numQuestions: 5, difficulty: "Medium", timerSeconds: 30 };
let notesText = "";
let questions = [];
let answers = {};
let currentIndex = 0;
let timerInterval = null;
let remaining = 0;
let historyData = [];
let lastResult = null;
let profile = { name: "Student", email: "student@quizforge.ai", photo: null };
let pendingPhoto = null;
let notifications = [];

/* ---------------- PDF Upload & Text Extraction (PDF.js client side) ---------------- */
function handlePdfSelect(file) {
  if (!file) return;

  if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
    showNotesError("Please choose a valid PDF file.");
    return;
  }

  hideNotesError();
  setUploadState("loading", file.name);
  extractPdfText(file);
}

async function extractPdfText(file) {
  try {
    if (!window.pdfjsLib) {
      throw new Error("PDF parser library unavailable. Please paste your text directly.");
    }

    const arrayBuffer = await file.arrayBuffer();
    const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer });
    const pdf = await loadingTask.promise;

    let fullText = "";
    // Limit page extraction to 30 pages max to prevent memory bloat
    const maxPages = Math.min(pdf.numPages, 30);

    for (let i = 1; i <= maxPages; i++) {
      const page = await pdf.getPage(i);
      const textContent = await page.getTextContent();
      const pageText = textContent.items.map(item => item.str).join(" ");
      fullText += pageText + "\n\n";
    }

    const trimmed = fullText.trim();
    if (!trimmed) {
      throw new Error("Could not extract readable text from this PDF file.");
    }

    console.log(`[PDF.js] PDF pages processed: ${maxPages}`);
    console.log(`[PDF.js] Extracted text length: ${trimmed.length}`);

    document.getElementById("notes-input").value = trimmed;
    setUploadState("done", file.name, formatBytes(file.size) + ` (${maxPages} pgs)`);
    toast("Notes extracted from PDF");
  } catch (err) {
    setUploadState("idle");
    showNotesError(err.message || "Couldn't read PDF. Please try another file or paste your notes.");
  }
}

function setUploadState(state, filename, meta) {
  const zone = document.getElementById("upload-zone");
  const chip = document.getElementById("file-chip");
  const icon = document.getElementById("upload-icon");
  const title = document.getElementById("upload-title");
  const subtitle = document.getElementById("upload-subtitle");

  if (state === "loading") {
    zone.classList.remove("hidden");
    chip.classList.add("hidden");
    icon.innerHTML = '<span class="spinner"></span>';
    title.textContent = "Reading " + filename + "…";
    subtitle.textContent = "Extracting text from PDF";
  } else if (state === "done") {
    zone.classList.add("hidden");
    chip.classList.remove("hidden");
    document.getElementById("file-name").textContent = filename;
    document.getElementById("file-meta").textContent = (meta || "") + " · Text extracted";
  } else {
    zone.classList.remove("hidden");
    chip.classList.add("hidden");
    icon.innerHTML = "&#128196;";
    title.textContent = "Upload a PDF";
    subtitle.textContent = "Tap to choose a file, or drag one here";
  }
}

function removePdf(evt) {
  evt.stopPropagation();
  document.getElementById("pdf-input").value = "";
  setUploadState("idle");
}

function formatBytes(bytes) {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

function showNotesError(msg) {
  const errEl = document.getElementById("notes-error");
  errEl.textContent = msg;
  errEl.classList.remove("hidden");
}
function hideNotesError() {
  document.getElementById("notes-error").classList.add("hidden");
}

(function setupDragAndDrop() {
  const zone = document.getElementById("upload-zone");
  if (!zone) return;
  ["dragenter", "dragover"].forEach((evt) =>
    zone.addEventListener(evt, (e) => {
      e.preventDefault();
      zone.classList.add("dragover");
    })
  );
  ["dragleave", "drop"].forEach((evt) =>
    zone.addEventListener(evt, (e) => {
      e.preventDefault();
      zone.classList.remove("dragover");
    })
  );
  zone.addEventListener("drop", (e) => {
    const file = e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) handlePdfSelect(file);
  });
})();

/* ---------------- Navigation ---------------- */
function goTo(screen) {
  document.querySelectorAll(".screen").forEach((s) => s.classList.add("hidden"));
  document.getElementById("screen-" + screen).classList.remove("hidden");
  document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.screen === screen));
  document.getElementById("screen-" + screen).scrollTop = 0;
  if (screen === "history") renderHistory();
  if (screen === "settings") { renderProfile(); renderAudioUI(); }
  if (screen !== "quiz") {
    stopTimer();
    stopBackgroundMusic();
  }
}
document.querySelectorAll(".nav-item").forEach((btn) => {
  btn.addEventListener("click", () => goTo(btn.dataset.screen));
});

let toastTimer;
function toast(msg) {
  const el = document.getElementById("toast");
  el.textContent = msg;
  el.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 1800);
}

/* ---------------- AI Quiz Generation ---------------- */
async function generateQuiz() {
  hideNotesError();
  notesText = document.getElementById("notes-input").value.trim();

  if (notesText.length < 15) {
    showNotesError("Please paste at least a few sentences of notes so QuizForge AI has enough material.");
    return;
  }

  const n = Math.max(3, Math.min(30, parseInt(document.getElementById("num-questions").value) || 5));
  const difficulty = document.getElementById("difficulty-select").value;
  settings.numQuestions = n;
  settings.difficulty = difficulty;

  const btn = document.getElementById("generate-btn");
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner"></span> Generating quiz…';

  try {
    const data = await apiFetch("/api/quiz/generate", {
      method: "POST",
      body: JSON.stringify({ notes: notesText, numQuestions: n, questionCount: n, difficulty: difficulty }),
    });

    if (!data || !data.success || !data.questions || data.questions.length === 0) {
      throw new Error((data && data.error) || "Could not generate quiz from these notes. Please try again.");
    }

    questions = data.questions;
    answers = {};
    currentIndex = 0;
    startQuiz();
  } catch (err) {
    showNotesError(err.message || "Something went wrong generating your quiz.");
  } finally {
    btn.disabled = false;
    btn.innerHTML = "&#10024; Generate Quiz";
  }
}

/* ---------------- Quiz Screen ---------------- */
function startQuiz() {
  document.getElementById("quiz-difficulty").textContent = settings.difficulty;
  currentIndex = 0;
  goTo("quiz");
  startBackgroundMusic();
  renderQuestion();
}

function renderQuestion() {
  const q = questions[currentIndex];
  const total = questions.length;

  document.getElementById("progress-label").textContent = `Question ${currentIndex + 1} of ${total}`;
  document.getElementById("progress-pct").textContent = Math.round(((currentIndex + 1) / total) * 100) + "%";
  document.getElementById("progress-fill").style.width = Math.round(((currentIndex + 1) / total) * 100) + "%";

  document.getElementById("q-text").textContent = q.question;

  const optsEl = document.getElementById("q-options");
  optsEl.innerHTML = "";
  const letters = ["A", "B", "C", "D"];
  q.options.forEach((opt, i) => {
    const b = document.createElement("button");
    b.className = "option" + (answers[currentIndex] === i ? " selected" : "");
    b.innerHTML = `<span class="letter">${letters[i]}</span><span>${opt}</span>`;
    b.onclick = () => {
      answers[currentIndex] = i;
      renderQuestion();
    };
    optsEl.appendChild(b);
  });

  document.getElementById("prev-btn").disabled = currentIndex === 0;
  const nextBtn = document.getElementById("next-btn");
  nextBtn.textContent = currentIndex === total - 1 ? "Submit Quiz" : "Next";
  nextBtn.onclick = currentIndex === total - 1 ? submitQuiz : nextQuestion;

  startTimer();
}

function nextQuestion() {
  if (currentIndex < questions.length - 1) {
    currentIndex++;
    renderQuestion();
  }
}

function prevQuestion() {
  if (currentIndex > 0) {
    currentIndex--;
    renderQuestion();
  }
}

function startTimer() {
  stopTimer();
  remaining = settings.timerSeconds;
  updateTimerPill();
  timerInterval = setInterval(() => {
    remaining--;
    updateTimerPill();
    if (remaining > 0 && remaining <= 5) soundTick();
    if (remaining <= 0) {
      stopTimer();
      if (currentIndex === questions.length - 1) submitQuiz();
      else nextQuestion();
    }
  }, 1000);
}

function stopTimer() {
  if (timerInterval) {
    clearInterval(timerInterval);
    timerInterval = null;
  }
}

function updateTimerPill() {
  const pill = document.getElementById("timer-pill");
  const m = String(Math.floor(remaining / 60)).padStart(2, "0");
  const s = String(remaining % 60).padStart(2, "0");
  pill.textContent = `\u23F1 ${m}:${s}`;
  pill.classList.toggle("low", remaining <= 10);
}

/* ---------------- Submit & Result ---------------- */
async function submitQuiz() {
  stopTimer();
  stopBackgroundMusic();
  let correct = 0;
  questions.forEach((q, i) => {
    if (answers[i] === q.correctAnswer) correct++;
  });
  const total = questions.length;
  const wrong = total - correct;
  const percentage = Math.round((correct / total) * 100);

  lastResult = { total, correct, wrong, score: correct, percentage };

  if (percentage >= 50) setTimeout(soundCorrect, 300);
  else setTimeout(soundWrong, 300);

  renderResult();
  goTo("result");

  try {
    await apiFetch("/api/history", {
      method: "POST",
      body: JSON.stringify({ total, correct, wrong, score: correct, percentage, difficulty: settings.difficulty }),
    });
  } catch (err) {
    console.warn("Couldn't save quiz result:", err.message);
  }
  await refreshNotifications();
}

function renderResult() {
  const { total, correct, wrong, score, percentage } = lastResult;
  document.getElementById("score-ring").style.setProperty("--pct", percentage);
  document.getElementById("score-pct").textContent = percentage + "%";
  document.getElementById("stat-total").textContent = total;
  document.getElementById("stat-correct").textContent = correct;
  document.getElementById("stat-wrong").textContent = wrong;
  document.getElementById("stat-score").textContent = score;

  let msg = "Worth another pass through the notes.";
  if (percentage >= 90) msg = "Outstanding! You've mastered this material.";
  else if (percentage >= 75) msg = "Great job — you know this well.";
  else if (percentage >= 50) msg = "Good effort — a quick review will help.";
  document.getElementById("perf-msg").textContent = msg;

  const listEl = document.getElementById("review-list");
  listEl.innerHTML = "";
  const letters = ["A", "B", "C", "D"];
  questions.forEach((q, i) => {
    const wrap = document.createElement("div");
    wrap.className = "review-item";
    const optsHtml = q.options
      .map((opt, oi) => {
        let cls = "option";
        if (oi === q.correctAnswer) cls += " correct";
        else if (answers[i] === oi) cls += " wrong";
        return `<button class="${cls}" disabled><span class="letter">${letters[oi]}</span><span>${opt}</span></button>`;
      })
      .join("");
    wrap.innerHTML = `
      <div class="glass-card" style="border-radius:16px 16px 0 0;">
        <span class="tab-pill">Q${i + 1}</span>
        <h3 style="margin-top:10px; font-size:15px;">${q.question}</h3>
        ${optsHtml}
      </div>
      <div class="explain-box"><b>Explanation:</b> ${q.explanation}</div>
    `;
    listEl.appendChild(wrap);
  });
}

/* ---------------- History ---------------- */
async function fetchHistory() {
  try {
    const data = await apiFetch("/api/history");
    if (data && data.success && data.history) {
      historyData = data.history;
    }
  } catch (err) {
    console.warn("Couldn't load quiz history:", err.message);
  }
}

async function renderHistory() {
  const listEl = document.getElementById("history-list");
  const impEl = document.getElementById("history-improvement");
  listEl.innerHTML = `<div class="empty-state">Loading history…</div>`;
  impEl.innerHTML = "";

  await fetchHistory();

  listEl.innerHTML = "";

  if (historyData.length === 0) {
    listEl.innerHTML = `<div class="empty-state">No quizzes completed yet. Take a quiz to see your progress here!</div>`;
    return;
  }

  if (historyData.length >= 2) {
    const diff = historyData[0].percentage - historyData[historyData.length - 1].percentage;
    const div = document.createElement("div");
    div.className = "glass-card";
    div.style.margin = "0 20px 16px";
    div.innerHTML = `<p style="margin:0; font-size:12.5px; color:var(--text-dim);">Change since your first quiz</p>
      <h2 style="margin:2px 0 0; color:${diff >= 0 ? "var(--good)" : "var(--bad)"};">${diff >= 0 ? "+" : ""}${diff}%</h2>`;
    impEl.appendChild(div);
  }

  historyData.forEach((h) => {
    const div = document.createElement("div");
    div.className = "history-item";
    const badgeClass = h.percentage >= 75 ? "good" : "mid";
    const formattedDate = h.date ? new Date(h.date).toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" }) : "Recent";
    div.innerHTML = `
      <div>
        <div class="d">${formattedDate}</div>
        <h4>${h.correct}/${h.total} correct</h4>
      </div>
      <span class="badge ${badgeClass}">${h.percentage}%</span>
    `;
    listEl.appendChild(div);
  });
}

/* ---------------- Theme Toggle (Nebula dark / Aurora light) ---------------- */
function setTheme(mode) {
  const phone = document.querySelector(".phone");
  if (mode === "light") {
    phone.setAttribute("data-theme", "light");
  } else {
    phone.removeAttribute("data-theme");
  }
  document.getElementById("theme-btn-light").classList.toggle("active", mode === "light");
  document.getElementById("theme-btn-dark").classList.toggle("active", mode === "dark");
  settings.theme = mode;
  try {
    localStorage.setItem("quizforge_theme", mode);
  } catch (e) {}
}

function loadTheme() {
  let mode = "dark";
  try {
    mode = localStorage.getItem("quizforge_theme") || "dark";
  } catch (e) {}
  setTheme(mode);
}

/* ---------------- Profile Management ---------------- */
function initials(name) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0][0].toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function setAvatarDisplay(initialsId, imgId, photoSrc, name) {
  const imgEl = document.getElementById(imgId);
  const initialsEl = document.getElementById(initialsId);
  if (photoSrc) {
    imgEl.src = photoSrc;
    imgEl.classList.remove("hidden");
    initialsEl.classList.add("hidden");
  } else {
    imgEl.classList.add("hidden");
    imgEl.removeAttribute("src");
    initialsEl.classList.remove("hidden");
    initialsEl.textContent = initials(name);
  }
}

async function fetchProfile() {
  try {
    const data = await apiFetch("/api/profile");
    if (data && data.success && data.profile) {
      profile = data.profile;
    }
  } catch (err) {
    console.warn("Couldn't load profile:", err.message);
  }
}

function renderProfile() {
  document.getElementById("profile-name").textContent = profile.name;
  document.getElementById("profile-email").textContent = profile.email;
  setAvatarDisplay("profile-avatar-initials", "profile-avatar-img", profile.photo, profile.name);

  const totalQuizzes = historyData.length;
  document.getElementById("profile-quizzes").textContent = totalQuizzes;
  if (totalQuizzes === 0) {
    document.getElementById("profile-avg").textContent = "—";
    document.getElementById("profile-best").textContent = "—";
    return;
  }
  const avg = Math.round(historyData.reduce((sum, h) => sum + h.percentage, 0) / totalQuizzes);
  const best = Math.max(...historyData.map((h) => h.percentage));
  document.getElementById("profile-avg").textContent = avg + "%";
  document.getElementById("profile-best").textContent = best + "%";
}

function triggerPhotoPicker() {
  document.getElementById("photo-input").click();
}

function handlePhotoSelect(file) {
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    toast("Please select an image file");
    return;
  }
  const reader = new FileReader();
  reader.onload = (e) => {
    pendingPhoto = e.target.result;
    if (document.getElementById("profile-edit-form").classList.contains("hidden")) {
      openProfileEdit();
    }
    setAvatarDisplay("edit-avatar-initials", "edit-avatar-img", pendingPhoto, profile.name);
  };
  reader.readAsDataURL(file);
}

function removePhoto() {
  pendingPhoto = "REMOVE";
  setAvatarDisplay("edit-avatar-initials", "edit-avatar-img", null, profile.name);
}

function openProfileEdit() {
  document.getElementById("edit-name-input").value = profile.name;
  document.getElementById("edit-email-input").value = profile.email;
  document.getElementById("edit-profile-error").classList.add("hidden");
  pendingPhoto = null;
  setAvatarDisplay("edit-avatar-initials", "edit-avatar-img", profile.photo, profile.name);
  document.getElementById("profile-view").classList.add("hidden");
  document.getElementById("profile-edit-form").classList.remove("hidden");
}

function cancelProfileEdit() {
  pendingPhoto = null;
  document.getElementById("profile-edit-form").classList.add("hidden");
  document.getElementById("profile-view").classList.remove("hidden");
}

async function saveProfileEdit() {
  const name = document.getElementById("edit-name-input").value.trim();
  const email = document.getElementById("edit-email-input").value.trim();
  const errEl = document.getElementById("edit-profile-error");

  if (!name) {
    errEl.textContent = "Please enter a name.";
    errEl.classList.remove("hidden");
    return;
  }
  const emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  if (!emailOk) {
    errEl.textContent = "Please enter a valid email address.";
    errEl.classList.remove("hidden");
    return;
  }
  errEl.classList.add("hidden");

  const payload = { name, email };
  if (pendingPhoto === "REMOVE") payload.remove_photo = true;
  else if (pendingPhoto) payload.photo = pendingPhoto;

  try {
    const data = await apiFetch("/api/profile", { method: "PUT", body: JSON.stringify(payload) });
    if (!data || !data.success || !data.profile) {
      throw new Error((data && data.error) || "Couldn't save your profile.");
    }
    profile = data.profile;
    pendingPhoto = null;
    renderProfile();
    cancelProfileEdit();
    syncMenuProfile();
    toast("Profile updated");
  } catch (err) {
    errEl.textContent = err.message || "Couldn't save profile.";
    errEl.classList.remove("hidden");
  }
}

/* ---------------- Audio & Background Music Engine ---------------- */
let audioSettings = {
  quizMusicEnabled: true,
  quizMusicVolume: 30,
  soundEffectsEnabled: true,
  soundEffectsVolume: 70
};

let audioCtx = null;
let bgAudio = null;

function getAudioCtx() {
  if (!audioCtx) {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    audioCtx = new Ctx();
  }
  if (audioCtx.state === "suspended") audioCtx.resume();
  return audioCtx;
}

function getBgAudio() {
  if (!bgAudio) {
    bgAudio = new Audio("audio/quiz_bg_music.wav");
    bgAudio.loop = true;
    bgAudio.volume = (audioSettings.quizMusicVolume / 100);
  }
  return bgAudio;
}

function startBackgroundMusic() {
  if (!audioSettings.quizMusicEnabled) return;
  try {
    const audio = getBgAudio();
    audio.volume = (audioSettings.quizMusicVolume / 100);
    const playPromise = audio.play();
    if (playPromise !== undefined) {
      playPromise.catch((err) => {
        console.warn("Background music play deferred until user interaction:", err);
      });
    }
  } catch (e) {
    console.warn("Couldn't play background music:", e);
  }
}

function stopBackgroundMusic() {
  if (bgAudio) {
    try {
      bgAudio.pause();
      bgAudio.currentTime = 0;
    } catch (e) {}
  }
}

function updateMusicVolume() {
  if (bgAudio) {
    bgAudio.volume = audioSettings.quizMusicEnabled ? (audioSettings.quizMusicVolume / 100) : 0;
  }
}

function toggleMusicEnabled() {
  audioSettings.quizMusicEnabled = !audioSettings.quizMusicEnabled;
  saveAudioSettings();
  renderAudioUI();
  if (audioSettings.quizMusicEnabled) {
    const currentScreen = document.querySelector(".screen:not(.hidden)")?.id;
    if (currentScreen === "screen-quiz") startBackgroundMusic();
  } else {
    stopBackgroundMusic();
  }
}

function onMusicVolumeChange(val) {
  audioSettings.quizMusicVolume = parseInt(val, 10);
  saveAudioSettings();
  renderAudioUI();
  updateMusicVolume();
}

function toggleSfxEnabled() {
  audioSettings.soundEffectsEnabled = !audioSettings.soundEffectsEnabled;
  saveAudioSettings();
  renderAudioUI();
}

function onSfxVolumeChange(val) {
  audioSettings.soundEffectsVolume = parseInt(val, 10);
  saveAudioSettings();
  renderAudioUI();
}

function renderAudioUI() {
  // Background Music UI
  const musicBtn = document.getElementById("music-toggle-btn");
  if (musicBtn) {
    musicBtn.textContent = audioSettings.quizMusicEnabled ? "ON" : "OFF";
    musicBtn.classList.toggle("active", audioSettings.quizMusicEnabled);
  }
  const musicPct = document.getElementById("music-volume-pct");
  if (musicPct) musicPct.textContent = audioSettings.quizMusicVolume + "%";
  const musicSlider = document.getElementById("music-volume-slider");
  if (musicSlider) {
    musicSlider.value = audioSettings.quizMusicVolume;
    musicSlider.style.setProperty("--fill", audioSettings.quizMusicVolume + "%");
  }

  // Sound Effects UI
  const sfxBtn = document.getElementById("sfx-toggle-btn");
  if (sfxBtn) {
    sfxBtn.textContent = audioSettings.soundEffectsEnabled ? "ON" : "OFF";
    sfxBtn.classList.toggle("active", audioSettings.soundEffectsEnabled);
  }
  const sfxPct = document.getElementById("sfx-volume-pct");
  if (sfxPct) sfxPct.textContent = audioSettings.soundEffectsVolume + "%";
  const sfxSlider = document.getElementById("sfx-volume-slider");
  if (sfxSlider) {
    sfxSlider.value = audioSettings.soundEffectsVolume;
    sfxSlider.style.setProperty("--fill", audioSettings.soundEffectsVolume + "%");
  }
}

function saveAudioSettings() {
  try {
    localStorage.setItem("quizforge_audio_settings", JSON.stringify(audioSettings));
  } catch (e) {}
}

function loadAudioSettings() {
  try {
    const stored = localStorage.getItem("quizforge_audio_settings");
    if (stored) {
      audioSettings = { ...audioSettings, ...JSON.parse(stored) };
    }
  } catch (e) {}
  renderAudioUI();
}

document.addEventListener("click", () => {
  const currentScreen = document.querySelector(".screen:not(.hidden)")?.id;
  if (currentScreen === "screen-quiz" && audioSettings.quizMusicEnabled && bgAudio && bgAudio.paused) {
    bgAudio.play().catch(() => {});
  }
}, { passive: true });

function playTone(freq = 440, duration = 0.12, type = "sine") {
  if (!audioSettings.soundEffectsEnabled || audioSettings.soundEffectsVolume <= 0) return;
  try {
    const ctx = getAudioCtx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.value = freq;
    const peak = (audioSettings.soundEffectsVolume / 100) * 0.18;
    gain.gain.setValueAtTime(0.0001, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(peak, ctx.currentTime + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + duration);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + duration + 0.02);
  } catch (e) {}
}

function soundCorrect() {
  playTone(660, 0.1, "sine");
  setTimeout(() => playTone(880, 0.12, "sine"), 90);
}
function soundWrong() {
  playTone(220, 0.16, "sawtooth");
}
function soundTick() {
  playTone(300, 0.05, "square");
}
function soundTap() {
  playTone(460, 0.045, "sine");
}

document.addEventListener("click", (e) => {
  const el = e.target.closest("button, .tile");
  if (el && !el.disabled) soundTap();
}, true);

/* ---------------- Side Menu ---------------- */
function openMenu() {
  closeNotifications();
  document.getElementById("menu-overlay").classList.add("show");
  document.getElementById("menu-drawer").classList.add("show");
  syncMenuProfile();
  highlightMenuActive();
}
function closeMenu() {
  document.getElementById("menu-overlay").classList.remove("show");
  document.getElementById("menu-drawer").classList.remove("show");
}
function menuNavigate(screen) {
  closeMenu();
  goTo(screen);
}
function syncMenuProfile() {
  document.getElementById("menu-name").textContent = profile.name;
  document.getElementById("menu-email").textContent = profile.email;
  setAvatarDisplay("menu-avatar-initials", "menu-avatar-img", profile.photo, profile.name);
}
function highlightMenuActive() {
  document.querySelectorAll(".menu-item").forEach((el) => el.classList.remove("active"));
  const current = document.querySelector(".screen:not(.hidden)")?.id?.replace("screen-", "");
  const map = { home: 0, notes: 1, history: 2, settings: 3 };
  const items = document.querySelectorAll(".menu-item");
  if (current in map) items[map[current]]?.classList.add("active");
}

/* ---------------- Notifications ---------------- */
function unreadCount() {
  return notifications.filter((n) => n.unread).length;
}

function renderNotifBadge() {
  const badge = document.getElementById("notif-badge");
  const count = unreadCount();
  if (count > 0) {
    badge.textContent = count > 9 ? "9+" : count;
    badge.classList.remove("hidden");
  } else {
    badge.classList.add("hidden");
  }
}

function renderNotifList() {
  const listEl = document.getElementById("notif-list");
  if (notifications.length === 0) {
    listEl.innerHTML = `<div class="notif-empty">You're all caught up &#127881;</div>`;
    return;
  }
  listEl.innerHTML = notifications
    .map(
      (n) => `
    <div class="notif-item ${n.unread ? "unread" : ""}" onclick="markNotificationRead(${n.id})">
      <div class="ni-icon" style="background:${n.color};">${n.icon}</div>
      <div class="ni-body">
        <h5>${n.title}</h5>
        <p>${n.body}</p>
        <div class="ni-time">${n.time}</div>
      </div>
      ${n.unread ? '<div class="ni-dot"></div>' : ""}
    </div>
  `
    )
    .join("");
}

async function refreshNotifications() {
  try {
    const data = await apiFetch("/api/notifications");
    if (data && data.success && data.notifications) {
      notifications = data.notifications;
    }
  } catch (err) {
    console.warn("Couldn't load notifications:", err.message);
  }
  renderNotifBadge();
}

async function toggleNotifications(evt) {
  evt.stopPropagation();
  const panel = document.getElementById("notif-panel");
  const isOpen = panel.classList.contains("show");
  closeMenu();
  if (isOpen) {
    panel.classList.remove("show");
  } else {
    await refreshNotifications();
    renderNotifList();
    panel.classList.add("show");
  }
}

function closeNotifications() {
  document.getElementById("notif-panel").classList.remove("show");
}

async function markNotificationRead(id) {
  const n = notifications.find((x) => x.id === id);
  if (n) n.unread = false;
  renderNotifList();
  renderNotifBadge();
  try {
    await apiFetch("/api/notifications/read", { method: "POST", body: JSON.stringify({ id }) });
  } catch (err) {}
}

async function markAllNotificationsRead() {
  notifications.forEach((n) => (n.unread = false));
  renderNotifList();
  renderNotifBadge();
  toast("All notifications marked as read");
  try {
    await apiFetch("/api/notifications/read", { method: "POST", body: JSON.stringify({}) });
  } catch (err) {}
}

document.addEventListener("click", (e) => {
  const panel = document.getElementById("notif-panel");
  if (panel && panel.classList.contains("show") && !panel.contains(e.target) && !e.target.closest(".icon-btn")) {
    closeNotifications();
  }
});

/* ---------------- Init ---------------- */
async function initApp() {
  loadTheme();
  loadAudioSettings();
  await fetchProfile();
  renderProfile();
  syncMenuProfile();
  await fetchHistory();
  await refreshNotifications();
}

initApp();
