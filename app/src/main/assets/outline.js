const ARTICLE_OUTLINE_SELECTOR =
  "#article-body-content h1, #article-body-content h2, " +
  "#article-body-content h3, #article-body-content h4";
const ARTICLE_OUTLINE_EXCLUDED_SELECTOR = ".ai-card, .ai-translation-row";
const ARTICLE_OUTLINE_MAX_HEADINGS = 40;

function articleOutlineHeadings() {
  return Array.from(document.querySelectorAll(ARTICLE_OUTLINE_SELECTOR))
    .filter((heading) => !heading.closest(ARTICLE_OUTLINE_EXCLUDED_SELECTOR))
    .filter((heading) => heading.textContent?.trim())
    .slice(0, ARTICLE_OUTLINE_MAX_HEADINGS);
}

function refreshArticleOutline() {
  const headings = articleOutlineHeadings();
  const candidates = headings.map((heading, domIndex) => ({
    domIndex,
    level: Number(heading.tagName.substring(1)),
    title: heading.textContent ?? "",
    id: heading.id || null,
  }));

  Android.updateArticleOutline(JSON.stringify(candidates));
}

function applyArticleOutline(targets) {
  const headings = articleOutlineHeadings();

  targets.forEach((target) => {
    const heading = headings[target.domIndex];
    if (heading) {
      heading.id = target.targetID;
    }
  });
}

function scrollToArticleHeading(targetID) {
  const heading = document.getElementById(targetID);
  if (!heading) return false;

  heading.scrollIntoView({ behavior: "smooth", block: "start" });
  return true;
}
