const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const mediaScript = fs.readFileSync(
  path.resolve(__dirname, "../../main/assets/media.js"),
  "utf8",
);

function loadMediaScript({ online = true } = {}) {
  const document = {
    createElement: () => ({
      className: "",
      textContent: "",
      remove() {
        this.removed = true;
      },
    }),
    getElementById: () => null,
    querySelectorAll: () => [],
  };
  const window = {
    addEventListener: () => {},
  };

  const context = vm.createContext({
    Android: {},
    URL,
    clearTimeout,
    document,
    navigator: { onLine: online },
    setTimeout,
    window,
  });
  vm.runInContext(mediaScript, context);

  return context;
}

function image({ cached = false } = {}) {
  const url = "https://img.example.com/broken.gif";

  return {
    complete: true,
    currentSrc: url,
    dataset: cached ? { capyImageId: "image-1" } : {},
    naturalWidth: 0,
    nextElementSibling: null,
    removed: false,
    src: url,
    getAttribute: () => url,
    insertAdjacentElement(_position, element) {
      this.nextElementSibling = element;
    },
    remove() {
      this.removed = true;
    },
  };
}

test("online remote image failures are removed without a diagnostic block", () => {
  const context = loadMediaScript();
  const failedImage = image();

  context.showImageLoadFailure(failedImage, { type: "error" });

  assert.equal(failedImage.removed, true);
  assert.equal(failedImage.nextElementSibling, null);
});

test("offline remote image failures remain visible for retry context", () => {
  const context = loadMediaScript({ online: false });
  const failedImage = image();

  context.showImageLoadFailure(failedImage, { type: "error" });

  assert.equal(failedImage.removed, false);
  assert.match(failedImage.nextElementSibling.textContent, /device is offline/);
});

test("cached image failures remain visible as local diagnostics", () => {
  const context = loadMediaScript();
  const failedImage = image({ cached: true });

  context.showImageLoadFailure(failedImage, { type: "error" });

  assert.equal(failedImage.removed, false);
  assert.match(
    failedImage.nextElementSibling.textContent,
    /cached file is unavailable or unreadable/,
  );
});
