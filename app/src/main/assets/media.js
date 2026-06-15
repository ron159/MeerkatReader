const EAGER_IMAGE_COUNT = 3;
const LONG_PRESS_DELAY_MS = 500;
const LONG_PRESS_MOVE_TOLERANCE_PX = 12;

function configureVideoTags() {
  [...document.getElementsByTagName("video")].forEach((v) => {
    v.setAttribute("preload", "auto");
    v.setAttribute("playsinline", "true");
    v.setAttribute("controls", "true");
    v.setAttribute("controlslist", "nofullscreen nodownload noremoteplayback");

    if (v.classList.contains("article__video-autoplay--looped")) {
      v.setAttribute("loop", "true");
      v.play();
    }
  });
}

function addImageClickListeners() {
  const images = [...document.getElementsByTagName("img")].filter(
    (img) => !img.classList.contains("iframe-embed__image"),
  );

  /** @type {MediaItem[]} */
  const galleryImages = images.map((i) => ({
    url: i.dataset.capyOriginalSrc || i.src,
    altText: i.alt || null,
    cachedImageId: i.dataset.capyImageId || null,
    originalUrl: i.dataset.capyOriginalSrc || null,
  }));

  images.forEach((img, index) => {
    img.addEventListener("click", (e) => {
      e.preventDefault();
      Android.openImageGallery(JSON.stringify(galleryImages), index);
    });

    longPress(img, (e) => {
      e.preventDefault();
      Android.showImageDialog(img.dataset.capyOriginalSrc || img.src);
    });
  });
}

function addImageLoadFailureListeners() {
  const content = document.getElementById("article-body-content");
  if (!content) return;

  content.querySelectorAll("img").forEach((img) => {
    if (img.classList.contains("iframe-embed__image")) {
      return;
    }

    if (img.dataset.capyLoadFailureListener === "true") {
      return;
    }

    img.dataset.capyLoadFailureListener = "true";
    img.addEventListener("load", () => removeImageLoadFailure(img));
    img.addEventListener("error", (event) =>
      showImageLoadFailure(img, event),
    );

    if (img.complete && img.naturalWidth === 0) {
      showImageLoadFailure(img);
    }
  });
}

/**
 * @param {HTMLImageElement} img
 * @param {Event=} event
 */
function showImageLoadFailure(img, event) {
  let label = img.nextElementSibling;

  if (!label?.classList.contains("image-load-error")) {
    label = document.createElement("div");
    label.className = "image-load-error";
    img.insertAdjacentElement("afterend", label);
  }

  label.textContent = imageLoadFailureMessage(img, event);
}

/**
 * @param {HTMLImageElement} img
 */
function removeImageLoadFailure(img) {
  const label = img.nextElementSibling;
  if (label?.classList.contains("image-load-error")) {
    label.remove();
  }
}

/**
 * @param {HTMLImageElement} img
 * @param {Event=} event
 * @returns string
 */
function imageLoadFailureMessage(img, event) {
  const source = img.dataset.capyImageId ? "cached" : "remote";
  const url = img.currentSrc || img.src || img.getAttribute("src") || "";
  const details = [
    "Image failed to load",
    `source: ${source}`,
  ];

  if (!url) {
    details.push("reason: missing image URL");
  } else if (source === "cached") {
    details.push("reason: cached file is unavailable or unreadable");
    details.push(`url: ${url}`);
  } else if (navigator.onLine === false) {
    details.push("reason: device is offline");
    details.push(`url: ${url}`);
  } else {
    details.push("reason: remote request failed or returned invalid image data");
    details.push(`url: ${url}`);
  }

  if (img.dataset.capyImageId) {
    details.push(`cache id: ${img.dataset.capyImageId}`);
  }

  if (img.dataset.capyOriginalSrc && img.dataset.capyOriginalSrc !== url) {
    details.push(`original: ${img.dataset.capyOriginalSrc}`);
  }

  if (event?.type) {
    details.push(`event: ${event.type}`);
  }

  return details.join("\n");
}

function primeArticleImages() {
  const content = document.getElementById("article-body-content");
  if (!content) return;

  [...content.querySelectorAll("img")]
    .filter((img) => !img.classList.contains("iframe-embed__image"))
    .slice(0, EAGER_IMAGE_COUNT)
    .forEach((img) => {
      if (typeof img.decode === "function") {
        img.decode().catch(() => {});
      }
    });
}

function addEmbedListeners() {
  [...document.querySelectorAll("div.iframe-embed")].forEach((div) => {
    div.addEventListener("click", () => {
      const iframe = document.createElement("iframe");
      iframe.src = div.getAttribute("data-iframe-src") || "";
      div.replaceWith(iframe);
    });
  });

  [...document.querySelectorAll("a")].forEach((anchor) => {
    longPress(anchor, () => {
      Android.showLinkDialog(anchor.href, anchor.text);
    });
  });
}

/**
 * @param {HTMLDivElement | Document} element
 * @returns boolean
 */
function cleanEmbeds(element = document) {
  const imgs = element.querySelectorAll("img");

  for (const img of imgs) {
    if (!img.src) {
      img.remove();
    }
  }

  const embeds = element.querySelectorAll("iframe");

  for (const embed of embeds) {
    const src = embed.getAttribute("src");
    if (!src) {
      continue;
    }

    const youtubeID = findYouTubeMatch(src);

    if (youtubeID !== null) {
      swapPlaceholder(embed, src, youtubeID);
    }
  }

  addEmbedListeners();
}

/**
 * @param {string} src
 */
function findYouTubeMatch(src) {
  for (const regex of YOUTUBE_DOMAINS) {
    const match = src.match(regex);
    if (match) {
      return match[1];
    }
  }
  return null;
}

/**
 * @param {HTMLIFrameElement} embed
 * @param {string} src
 * @param {string} youtubeID
 */
function swapPlaceholder(embed, src, youtubeID) {
  const placeholderImage = document.createElement("img");
  placeholderImage.classList.add("iframe-embed__image", "mercury-parser-keep");
  placeholderImage.setAttribute("src", imageURL(youtubeID));

  const playButton = document.createElement("div");
  playButton.classList.add("iframe-embed__play-button");

  const placeholder = document.createElement("a");
  placeholder.classList.add("iframe-embed");
  placeholder.setAttribute(
    "href",
    `https://www.youtube.com/watch?v=${youtubeID}`,
  );
  placeholder.appendChild(placeholderImage);
  placeholder.appendChild(playButton);

  embed.replaceWith(placeholder);
}

/** @param {string} id */
function imageURL(id) {
  return `https://img.youtube.com/vi/${id}/hqdefault.jpg`;
}

/**
 * @param {string} src
 * @returns string
 */
function autoplaySrc(src) {
  try {
    const url = new URL(src);
    url.searchParams.set("autoplay", "1");
    return url.toString();
  } catch (e) {
    return src;
  }
}

const YOUTUBE_DOMAINS = [
  /.*?\/\/www\.youtube-nocookie\.com\/embed\/(.*?)(\?|$)/,
  /.*?\/\/www\.youtube\.com\/embed\/(.*?)(\?|$)/,
  /.*?\/\/www\.youtube\.com\/user\/.*?#\w\/\w\/\w\/\w\/(.+)\b/,
  /.*?\/\/www\.youtube\.com\/v\/(.*?)(#|\?|$)/,
  /.*?\/\/www\.youtube\.com\/watch\?(?:.*?&)?v=([^&#]*)(?:&|#|$)/,
  /.*?\/\/youtube-nocookie\.com\/embed\/(.*?)(\?|$)/,
  /.*?\/\/youtube\.com\/embed\/(.*?)(\?|$)/,
  /.*?\/\/youtu\.be\/(.*?)(\?|$)/,
];

/**
 * Post-process article content: clean styles, resolve image URLs, wrap tables
 * @param {string} baseUrl
 * @param {boolean} hideImages
 */
function postProcessContent(baseUrl, hideImages) {
  const content = document.getElementById("article-body-content");
  if (!content) return;

  content.querySelectorAll("style").forEach((el) => el.remove());

  content.querySelectorAll("*").forEach((el) => {
    cleanAttributes(el);
  });

  cleanAttributes(document.body);

  content.querySelectorAll("a[onclick]").forEach((anchor) => {
    anchor.removeAttribute("onclick");
  });

  content.querySelectorAll("img").forEach((img, index) => {
    if (hideImages) {
      img.remove();
    } else {
      img.loading = index < EAGER_IMAGE_COUNT ? "eager" : "lazy";
      if (baseUrl) {
        const src = img.getAttribute("src");
        if (src && !src.startsWith("http") && !src.startsWith("data:")) {
          try {
            img.src = new URL(src, baseUrl).href;
          } catch (e) {
            // continue
          }
        }
      }
    }
  });

  content.querySelectorAll("table").forEach((table) => {
    if (table.parentElement?.classList.contains("table__wrapper")) return;
    const wrapper = document.createElement("div");
    wrapper.className = "table__wrapper";
    table.parentNode?.insertBefore(wrapper, table);
    wrapper.appendChild(table);
  });

  addImageLoadFailureListeners();
  primeArticleImages();
}

/**
 * @param {Element} element
 */
function cleanAttributes(element) {
  element.removeAttribute("style");
  element.removeAttribute("bgcolor");
  element.removeAttribute("color");
  element.removeAttribute("background");
}

/**
 * @param {HTMLElement} element
 * @param {(event: Event) => void} callback
 */
function longPress(element, callback) {
  /** @type {number | undefined} */
  let timer;
  let startX = 0;
  let startY = 0;
  let suppressNextClick = false;

  const clearTimer = () => {
    clearTimeout(timer);
    timer = undefined;
  };

  const pointFromEvent = (/** @type {MouseEvent | TouchEvent} */ event) => {
    if ("touches" in event && event.touches.length > 0) {
      return event.touches[0];
    }

    if ("changedTouches" in event && event.changedTouches.length > 0) {
      return event.changedTouches[0];
    }

    return /** @type {MouseEvent} */ (event);
  };

  const start = (/** @type {Event} */ event) => {
    const point = pointFromEvent(/** @type {MouseEvent | TouchEvent} */ (event));
    startX = point.clientX;
    startY = point.clientY;
    suppressNextClick = false;

    clearTimer();
    timer = setTimeout(() => {
      suppressNextClick = true;
      callback(event);
      timer = undefined;
    }, LONG_PRESS_DELAY_MS);
  };

  const move = (/** @type {Event} */ event) => {
    if (timer === undefined) {
      return;
    }

    const point = pointFromEvent(/** @type {MouseEvent | TouchEvent} */ (event));
    const deltaX = Math.abs(point.clientX - startX);
    const deltaY = Math.abs(point.clientY - startY);

    if (
      deltaX > LONG_PRESS_MOVE_TOLERANCE_PX ||
      deltaY > LONG_PRESS_MOVE_TOLERANCE_PX
    ) {
      clearTimer();
    }
  };

  const stop = () => {
    clearTimer();
  };

  element.addEventListener("mousedown", start);
  element.addEventListener("mousemove", move);
  element.addEventListener("mouseup", stop);
  element.addEventListener("mouseleave", stop);

  element.addEventListener("touchstart", start);
  element.addEventListener("touchmove", move);
  element.addEventListener("touchend", stop);
  element.addEventListener("touchcancel", stop);

  element.addEventListener(
    "click",
    (event) => {
      if (!suppressNextClick) {
        return;
      }

      event.preventDefault();
      event.stopImmediatePropagation();
      suppressNextClick = false;
    },
    true,
  );
}

window.addEventListener("DOMContentLoaded", () => {
  cleanEmbeds();
});

/**
 * @param {MessageEvent} event
 */
function handleBlueskyEmbedResize(event) {
  if (event.origin !== "https://embed.bsky.app") return;
  if (!event.data || typeof event.data.height !== "number") return;

  document.querySelectorAll("iframe").forEach((iframe) => {
    if (iframe.contentWindow === event.source) {
      iframe.style.height = event.data.height + "px";
    }
  });
}

window.addEventListener("message", handleBlueskyEmbedResize);

/** @type {string | null} */
let currentPlayingUrl = null;

/** @type {boolean} */
let isCurrentlyPlaying = false;

/**
 * Toggle audio playback - play if stopped, pause if playing
 * @param {string} url
 * @param {string} title
 * @param {string} feedName
 * @param {number | null} durationSeconds
 * @param {string} artworkUrl
 */
function playAudio(url, title, feedName, durationSeconds, artworkUrl) {
  if (currentPlayingUrl === url && isCurrentlyPlaying) {
    Android.pauseAudio();
  } else {
    const audioData = JSON.stringify({
      url: url,
      title: title,
      feedName: feedName,
      durationSeconds: durationSeconds,
      artworkUrl: artworkUrl || null,
    });
    Android.openAudioPlayer(audioData);
  }
}

/**
 * Update the play/pause state for an audio enclosure
 * Called from Android when play state changes
 * @param {string} url
 * @param {boolean} isPlaying
 */
function updateAudioPlayState(url, isPlaying) {
  currentPlayingUrl = url;
  isCurrentlyPlaying = isPlaying;

  const enclosures = document.querySelectorAll(".audio-enclosure");
  enclosures.forEach((enclosure) => {
    const enclosureUrl = enclosure.getAttribute("data-url");
    const playButton = enclosure.querySelector(".audio-enclosure__play-button");
    if (!playButton) return;

    if (enclosureUrl === url) {
      playButton.classList.toggle("playing", isPlaying);
    } else {
      playButton.classList.remove("playing");
    }
  });
}

/**
 * Reset all audio enclosures to not playing state
 * Called from Android when audio is dismissed
 */
function resetAudioPlayState() {
  currentPlayingUrl = null;
  isCurrentlyPlaying = false;
  const playButtons = document.querySelectorAll(
    ".audio-enclosure__play-button",
  );
  playButtons.forEach((btn) => btn.classList.remove("playing"));
}

window.onload = () => {
  addImageClickListeners();
  addEmbedListeners();
  configureVideoTags();
  addImageLoadFailureListeners();
  primeArticleImages();
  Android.requestAudioState();
};
