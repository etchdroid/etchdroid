# Contributing to EtchDroid

Thanks for taking the time to contribute! To maintain a high standard of code quality and a clean project history, please follow these guidelines.

## 🤝 Code of Conduct
EtchDroid follows the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/version/1/4/code-of-conduct/). In short: be helpful, don't take code critiques personally, and **Don't Be A Dick™️**.

---

## 🐛 Reporting Bugs & Suggestions
* **Check for duplicates:** Ensure the issue hasn't been reported or suggested already.
* **Troubleshoot hardware:** Many "bugs" are actually bad cables or sleep settings. Try writing while the device is on a table with the screen kept on.
* **Use the templates:** Fill out the requested information in the issue template so the bug can be reproduced.
* **Scope:** Features requiring root privileges or those outside the app's scope will not be accepted.

---

## 🌍 Translating
* **Weblate Only:** All translations must be done via [Weblate](https://hosted.weblate.org/engage/etchdroid/). **Pull requests for string files will be rejected.**
* **Credits:** After translating, you may send a PR to update [`about.xml`](https://github.com/EtchDroid/EtchDroid/blob/main/app/src/main/res/xml/about.xml) to be credited in the app.

---

## 🛠 Development Workflow

### 1. Linear History (No Merge Commits)
We use a **rebase-only workflow**. Your branch must be rebased onto the latest `main` branch.
* **Do:** Use `git rebase main`.
* **Do:** Use `git rebase -i main` to clean up your history.
* **Don't:** Use `git merge main` or the GitHub "Resolve conflicts" button.

### 2. Atomic & Concise Commits
* **One Thing Per Commit:** Each commit should perform one logical task. 
* **Small PRs:** If you are fixing multiple unrelated bugs, submit them as separate PRs.
* **No "Fixup" History:** Do not include commits that revert or fix changes introduced earlier in the same PR. Use `git commit --amend` or `git rebase -i` to squash mistakes before submitting.

### 3. Separate Cleanup from Logic
**Do not lump stylistic/formatting/cleanup changes into functional commits.**
* If you need to reformat code, do it in a dedicated commit (or separate PR).
* **Rule of thumb:** Do not fix what isn't broken unless it's necessary for your change.

### 4. Commit Messages
Be descriptive. We suggest (but don't require) the [Conventional Commits](https://www.conventionalcommits.org/) format (e.g., `feat(ui): add progress bar`).

---

## 💻 Coding Standards & Testing

* **Formatting:** Use default Android Studio formatting (`Ctrl+Alt+L`).
* **Clean Code:** Use constants instead of magic numbers and write self-documenting code. Comments should explain *why*, not *what*.
* **Testing:** * Run unit tests via Android Studio or `./gradlew test`.
    * Manually verify your changes by running the app and flashing a test image.

---

## 🚀 Pull Request Process
1. **Discuss Big Changes:** For significant features, open an issue first to ensure it aligns with project goals.
2. **Self-Review:** Review your own code for typos or leftover debug logs before submitting.
3. **Be Professional:** If changes are requested, explain your reasoning if you disagree. Disagreements are fine; the goal is to find a compromise.

By contributing, you agree that your code will be licensed under the project's [GPLv3 License](LICENSE).

