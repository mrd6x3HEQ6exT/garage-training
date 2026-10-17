# Learning Claude: A Hands-On Guide

Four tools, one Claude: Projects give Claude lasting context, Artifacts turn answers into things you can use, Claude Code works inside your code, and Cowork works inside your files and apps. Each lesson takes 10 to 20 minutes and ends with an exercise you do yourself. Do them in order the first time; after that, use the cheat sheet at the end.

| Tool | What it is | Reach for it when |
| --- | --- | --- |
| Projects | A workspace in claude.ai that remembers files and instructions across chats | You keep re-explaining the same background to Claude |
| Artifacts | A document, web page, app, or diagram Claude builds in a side panel and can publish | You want something to use or share, not just an answer |
| Claude Code | A coding agent in your terminal, IDE, or at claude.ai/code that reads, edits, tests, and commits code | You want changes made in a codebase, not described |
| Cowork | The desktop agent that works on files and connected apps on your computer | You want a non-coding task done end to end: files organised, a report built, email drafted |

One habit carries across all four. Tell Claude the goal, where the materials are, and what done looks like. Then review what it did before you trust it.

---

## Lesson 1 – Projects (10 minutes)

A Project is a folder of chats that share the same files and the same standing instructions. Set one up once, and every new chat inside it starts already knowing your background. Projects are available on every plan, including free, which allows up to five. Source: [What are Projects](https://support.claude.com/en/articles/9517075-what-are-projects).

**What goes where**

| Part | What it does | Good contents |
| --- | --- | --- |
| Project knowledge | Files Claude can read in every chat of the project | Reference docs, style guides, code snippets, past examples |
| Project instructions | Standing rules that apply to every chat | Role, goal, tone, rules, things to never do |
| Chats | Individual conversations | One task per chat; chats do not share context with each other, only with the knowledge base |

**Setup, step by step**

1. Go to Projects in the left sidebar, or open claude.ai/projects, and click "+ New Project".
2. Name it. The description is for you; Claude does not read it.
3. On the right, click "+" in the knowledge area and upload your reference files.
4. Click "Set project instructions", write them in three parts (role, goal, rules), then "Save instructions".
5. Start a chat inside the project and ask something only the uploaded files could answer, to confirm it is reading them.

On paid plans, when knowledge grows past what fits in one conversation, Claude switches to retrieval mode and pulls only the relevant parts, which expands capacity up to ten times. On Team and Enterprise, "Share project" lets colleagues view or edit. Steps and limits: [How can I create and manage Projects](https://support.claude.com/en/articles/9519177-how-can-i-create-and-manage-projects).

**Exercise**

- [ ] Create a project called "Garage Training".
- [ ] Upload this repo's README and one other file you refer to often.
- [ ] Write instructions: "You help me maintain a small workout web app. Keep answers short. Always ask before suggesting a rewrite."
- [ ] Open a new chat and ask: "Based on the files, what does this app do?"
- [ ] Open a second chat and notice it does not remember the first chat, but does know the files.

**Tip:** instructions beat knowledge for behaviour, and knowledge beats instructions for facts. Put "how to answer" in instructions and "what is true" in files.

---

## Lesson 2 – Artifacts (10 minutes)

An Artifact is a piece of work Claude builds in a panel beside the chat instead of inside it: a document, a web page, a working app, a diagram, a slide deck. You can edit it, ask for changes, keep versions, and share it by link.

**How to get one**

Ask for a thing, not an answer. "Make me a one-page checklist I can print" produces an artifact; "what should I check?" produces a chat reply. Words that reliably trigger one: build, make, create, a page, an app, a tool, a document, a diagram, a tracker.

**Iterating**

1. Ask for the first version in one sentence with the purpose and the audience.
2. Look at it. Reply with specific changes: "move the total to the top, make the buttons bigger".
3. Use the version arrows in the panel to compare and go back if a change made it worse.
4. When it is right, share it.

**Sharing, by plan**

| Plan | Button | Who can see it |
| --- | --- | --- |
| Free, Pro, Max | "Publish" then copy the link, or "Share" and pick "Anyone with the link" | Anyone with the link, no account needed |
| Team, Enterprise | "Share" then "Share & copy link" | People in your organisation, unless an owner enables external sharing |

New artifacts are private until shared. Publishing adds the artifact to the "Artifacts" section of your sidebar, so you can find it later without the chat. "Unpublish" revokes the link for good and deletes any data the artifact stored. Source: [Publish and share artifacts](https://support.claude.com/en/articles/9547008-publish-and-share-artifacts).

**What artifacts can do beyond static pages**

- Hold shared state, so a sign-up sheet or tracker remembers entries from every viewer.
- Call Claude from inside the page, so a tool can answer questions itself.
- Collect comments. On a document, select text and comment, and Claude can read and reply.

**Exercise**

- [ ] In a chat, ask: "Build a simple rest-timer web app with 30, 60 and 90 second buttons and a big countdown."
- [ ] Ask for two changes, one at a time: a dark background, and a beep when the timer ends.
- [ ] Use the version arrows to compare version 1 and version 3.
- [ ] Publish it and open the link on your phone.

**Tip:** ask for small changes one message at a time. A list of six changes in one message often gets four right and two wrong, and then you cannot tell which version to go back to.

---

## Lesson 3 – Claude Code (20 minutes)

Claude Code is Claude with hands on your codebase. It reads files, edits them, runs tests and commands, and commits, asking permission as it goes. It runs in a terminal, in VS Code and JetBrains, as a desktop app, and in the browser at claude.ai/code, where it works in a cloud copy of your repo. Source: [Quickstart](https://code.claude.com/docs/en/quickstart).

**Install and sign in**

```bash
# macOS, Linux, WSL
curl -fsSL https://claude.ai/install.sh | bash

# Windows PowerShell
irm https://claude.ai/install.ps1 | iex

# then, in your project folder
claude
```

The first run opens your browser to sign in with your Claude subscription. Check it worked with `claude --version`. If the terminal route is not for you, claude.ai/code runs the same agent against a GitHub repo with nothing to install.

**Your first session**

1. Start with a question, not a task: "what does this project do?" Claude reads the files itself; you do not need to paste them.
2. Run `/init`. Claude writes a CLAUDE.md file describing your project, its commands and conventions. It reads this file at the start of every session, so anything you find yourself repeating goes here.
3. Ask for a small change: "add a comment at the top of index.html explaining what the app does". Approve the edit when asked.
4. Ask it to commit: "commit that with a descriptive message".

**The commands and keys that matter**

| Do this | To get |
| --- | --- |
| `Shift+Tab` | Cycle permission modes. Plan mode reads and proposes but edits nothing until you approve. Use it for anything bigger than a one-file change. |
| `@path/to/file` | Put a specific file in front of Claude. Type `@` to get a picker. |
| `/clear` | Fresh conversation, CLAUDE.md still loaded. Do this between unrelated tasks. |
| `/compact` | Shrink a long conversation while keeping the gist. |
| `/resume` or `claude -c` | Pick up a previous session. |
| `/help` | List everything available, including skills. Type `/` alone to browse. |
| `Esc` | Stop Claude mid-action. |
| Paste an image | Screenshots of errors or designs work as context. |

**Three habits of people who get good results**

- Be specific. "Fix the bug where the timer keeps running after Ungroup" beats "fix the timer".
- Make Claude prove it. End tasks with "run the tests" or "show me it working". A change that only looks right is not done.
- Keep CLAUDE.md honest. When Claude does something wrong twice, add a line to CLAUDE.md so it stops. The mistake.md file in this repo is the same idea.

**Exercise, using this repo**

- [ ] Open claude.ai/code, pick the garage-training repo, or clone it and run `claude` inside it.
- [ ] Ask: "Explain how workouts are generated in index.html, in five bullets."
- [ ] Press `Shift+Tab` into plan mode and ask: "How would you add a 'skip exercise' button? Don't change anything yet." Read the plan.
- [ ] Approve the plan, let it build, then ask: "Run tests.js and tell me if anything failed."
- [ ] Ask it to commit on a new branch and open a draft pull request. Review the diff on GitHub before merging.

**Tip:** when a session goes sideways, do not argue with it. `/clear`, then restate the task with what you learned. Fresh context fixes more than clever prompting. More recipes: [Common workflows](https://code.claude.com/docs/en/common-workflows).

---

## Lesson 4 – Cowork (15 minutes)

Cowork is Claude Code's approach applied to everything that is not code: it works through multi-step tasks in your files and connected apps, proposes a plan, asks before consequential actions, and reports back. It needs a paid plan (Pro, Max, Team or Enterprise). Local folder access needs the desktop app for macOS or Windows; web and mobile can run cloud tasks with connectors. Source: [Get started with Cowork](https://support.claude.com/en/articles/13345190-get-started-with-cowork).

**Starting a task**

1. Open Claude and pick "Cowork" in the message box. If you see no Chat or Cowork choice, every conversation can already take Cowork tasks.
2. On desktop, attach a folder with the folder icon. Claude can only read and write inside folders you connect.
3. Describe the task with the outcome: "Go through the PDFs in this folder, pull out every invoice total, and put them in a spreadsheet sorted by date."
4. Read the plan Claude proposes, then let it run. It will ask before sending, deleting, or changing files outside the folder.

**Approval modes**

| Mode | What happens | Use when |
| --- | --- | --- |
| Manual (default) | Claude asks before each action; you Allow or Deny | Learning, or anything touching email or money |
| Auto | Reads are free; writes and deletes are safety-checked, unsafe ones blocked | Routine tasks you have watched succeed before |
| Skip all approvals | Nothing pauses, nothing is checked | Almost never |

**Connectors and instructions**

- Add apps (Gmail, Calendar, Drive, Slack, GitHub and more) through the "+" menu or Customize > Connectors. Each connector tool can be set to "Always allow", "Needs approval", or "Blocked".
- Global instructions live under Settings > Cowork and apply to every task. Folder instructions apply to one connected folder, like a CLAUDE.md for your documents.
- Plugins bundle skills and connectors for a role, and once added they work in chat and Claude Code too.

**Scheduling**

Type `/schedule` in any Cowork task, or click "Scheduled" in the sidebar. Scheduled tasks run in the cloud, so your computer can be off. Write the prompt as a complete instruction, because nobody will be there to answer questions.

**Exercise**

- [ ] Make a folder with five or six mixed files: a couple of PDFs, a photo, a text note, a spreadsheet.
- [ ] Start a Cowork task: "Rename every file in this folder to the pattern YYYY-MM-DD-description, using the file's own date and contents. Show me the mapping before renaming."
- [ ] Approve or deny each step and watch what it does.
- [ ] Second task: "Write a one-page summary of what is in this folder as a Word document."
- [ ] Open the Scheduled page and set a weekly task: "Every Monday at 8am, list the five oldest files in my Downloads folder and suggest which to delete."

**Tip:** Cowork is at its best when you give it a folder and a finish line and get out of the way. It is at its worst when the task lives half in your head. Write down what done looks like before you start.

---

## Lesson 5 – Skills (10 minutes)

A skill is a folder with a SKILL.md file that teaches Claude a repeatable workflow: how to run this repo's tests, how you like commits written, how to build a document in your house style. Claude loads a skill on its own when your request matches its description, or you run it by name with a slash command. The same idea works in Claude Code, claude.ai and Cowork. Sources: [Skills in Claude Code](https://code.claude.com/docs/en/skills), [Using skills in Claude](https://support.claude.com/en/articles/12512180-using-skills-in-claude).

**In Claude Code**

| Scope | Where it lives | Notes |
| --- | --- | --- |
| Project | `.claude/skills/<name>/SKILL.md` | Commit it so everyone on the repo gets it |
| Personal | `~/.claude/skills/<name>/SKILL.md` | Follows you across all projects |
| Plugin | Installed as a bundle | Run as `/plugin-name:skill-name` |

The smallest useful skill is a few lines:

```markdown
---
name: run-tests
description: Run the test suite in tests.js and report any failures
---

Run the tests in tests.js with node. Report each failing test by name
with the assertion message. Do not change any code unless asked.
```

Only the description is really needed; Claude reads it to decide when the skill applies. Add `disable-model-invocation: true` to make a skill run only when you call it, which is right for anything that deploys or sends. Custom slash commands in `.claude/commands/` are the same thing and still work. Type `/skills` to list what is loaded, or `/` to browse.

**In claude.ai and Cowork**

- Skills need code execution on: Settings > Capabilities, then "Code execution and file creation".
- Built-in skills for Excel, Word, PowerPoint and PDF are already there and run on their own when you ask for one of those files. Toggle them under Customize > Skills.
- To add your own: zip the skill folder, go to Customize > Skills, click "+", then "+ Create skill" and "Upload a skill".
- Plugins bundle skills with connectors for a role, and once added they work in chat, Cowork and Claude Code.

**Exercise, using this repo**

- [ ] Create `.claude/skills/run-tests/SKILL.md` with the example above.
- [ ] Start `claude` in the repo and type `/skills`. Confirm run-tests is listed.
- [ ] Type `/run-tests` and read the report.
- [ ] Start a new session and ask "are the tests passing?" without naming the skill. Watch whether Claude picks it up on its own.
- [ ] In claude.ai, turn on the Word skill under Customize > Skills and ask for a one-page workout plan as a Word document.

**Tip:** CLAUDE.md is for facts that are always true about the project. A skill is for a procedure you would otherwise explain every time. If you catch yourself typing the same three-step instruction twice, that is a skill.

---

## Putting it together: a one-week practice plan

Each day is twenty minutes on one real thing you already care about. By Friday the four tools are working on the same project, which is where they pay off.

| Day | Tool | Task |
| --- | --- | --- |
| Mon | Projects | Create the Garage Training project, upload README and mistake.md, write instructions |
| Tue | Artifacts | In that project, ask for a printable workout log sheet. Iterate twice, publish, open on your phone |
| Wed | Claude Code | Open the repo at claude.ai/code. Ask it to explain tests.js, then add one new test in plan mode first |
| Thu | Cowork | Point it at your workout photos or notes folder. Ask for a monthly summary as a document |
| Fri | All four | Ask Claude Code for a change, have it open a PR, and ask Cowork to draft the release note from the PR |
| Weekend | Review | Add to CLAUDE.md or project instructions the three things you had to repeat this week |

The weekend step is the one most people skip and the one that compounds. Every repeated correction that moves into instructions is one you never make again.

---

## Quick-reference cheat sheet

**Claude Code**

| Command or key | Does |
| --- | --- |
| `claude` | Start in the current folder |
| `claude -c` | Continue the last session here |
| `claude -p "question"` | One answer, then exit. Pipes work: `git diff \| claude -p "review this"` |
| `/init` | Write a CLAUDE.md for this project |
| `/clear` | New conversation, same project memory |
| `/compact` | Compress a long conversation |
| `/resume` | Pick an earlier session |
| `/help` | Everything available, including skills |
| `Shift+Tab` | Cycle permission modes, including plan mode |
| `@file` | Put a file in context |
| `Esc` | Stop what Claude is doing |

**Cowork**

| Where | Does |
| --- | --- |
| Folder icon | Connect a local folder (desktop app) |
| Mode selector | Manual, Auto, or Skip approvals |
| `/schedule` | Make the current task recurring, runs in the cloud |
| "+" menu | Add connectors |
| Settings > Cowork | Global instructions |

**Skills**

| Where | Does |
| --- | --- |
| `/skills` | List the skills loaded in this session |
| `/skill-name` | Run a skill by hand, with arguments after it |
| `.claude/skills/<name>/SKILL.md` | Project skill, committed with the repo |
| `~/.claude/skills/<name>/SKILL.md` | Personal skill, every project |
| Customize > Skills (claude.ai, Cowork) | Toggle built-in skills, upload your own as a zip |

**Projects and Artifacts**

| Where | Does |
| --- | --- |
| claude.ai/projects, "+ New Project" | Create a project |
| "+" in the knowledge panel | Upload files Claude reads in every chat |
| "Set project instructions" | Standing rules for every chat |
| "Publish" or "Share" on an artifact | Make a link |
| Sidebar > Artifacts | Find published artifacts later |

**Prompting, for all four**

- State the goal, where the materials are, and what done looks like.
- One task per chat or session. Start fresh for the next one.
- Ask for proof: run the tests, show the output, list what changed.
- Small changes, one message each, so you can tell which one broke it.
- Say what went wrong in instructions or CLAUDE.md, not in the chat, so it sticks.

**Where to go deeper**

- [Claude Code docs](https://code.claude.com/docs/en/quickstart) and the free [Claude Code 101 course](https://academy.claude.com/courses/claude-code-101)
- [Cowork help](https://support.claude.com/en/articles/13345190-get-started-with-cowork)
- [Projects help](https://support.claude.com/en/articles/9519177-how-can-i-create-and-manage-projects)
- [Artifacts help](https://support.claude.com/en/articles/9547008-publish-and-share-artifacts)
