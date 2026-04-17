# Design System Strategy: The Technical Curator

## 1. Overview & Creative North Star
The Creative North Star for this design system is **"The Technical Curator."** 

Unlike consumer AI which focuses on "magic," this system celebrates the "mechanism." It is designed for the researcher who values the process of discovery as much as the result. We break the "SaaS template" look by leaning into a high-density, engineering-heavy aesthetic that prioritizes information velocity and evidentiary depth. The visual language is inspired by laboratory instrumentation and precision engineering: it is austere, authoritative, and unapologetically functional.

We avoid the "friendly" softness of modern web apps. Instead, we use intentional asymmetry—such as right-aligned technical metadata contrasting with left-aligned narrative text—and a rigorous tonal hierarchy to create a sense of professional-grade reliability.

---

## 2. Color Theory: The Graphite Spectrum
The palette is a disciplined range of graphite, slate, and blue-grays. It is designed to recede, allowing the researcher’s data and the AI’s "evidence" to take center stage.

- **Primary (`#546067`) & Secondary (`#466370`):** These are not "brand colors" in the traditional sense; they are functional accents. Use them for active states and critical path actions.
- **The "No-Line" Rule:** Standard 1px borders are prohibited for layout sectioning. Boundaries are defined by background shifts. For example, the main workspace uses `surface`, while the RAG retrieval sidebar uses `surface-container-low`.
- **Surface Hierarchy & Nesting:** Depth is achieved through a "Monolithic Stacking" model.
    - **Base Layer:** `background` (#f8f9fa)
    - **Structural Zones:** `surface-container-low` or `surface-container`
    - **Interactive Elements:** `surface-container-lowest` (pure white) to draw the eye to input areas.
- **The "Glass & Gradient" Rule:** Use `surface-container-highest` with a 0.8 opacity and a 12px backdrop-blur for floating command palettes. This ensures the "technical" background data remains visible but diffused, maintaining the context of the research.
- **Signature Textures:** Apply a subtle linear gradient from `primary` to `primary-dim` on primary action buttons to give them a "machined metal" feel, rather than a flat plastic appearance.

---

## 3. Typography: The Dual-Script System
The system employs a dual-font strategy to distinguish between "Human Narrative" and "Machine Logic."

- **Narrative (Inter):** Used for all `display`, `headline`, and `body` roles. It provides the clean, neutral readability required for long-form research analysis. 
    - *Editorial Note:* Use `body-md` for the majority of the UI to maintain high information density.
- **Technical (Space Grotesk / Monospaced):** Reserved for `label-md` and `label-sm`. These tokens must be used for all RAG metadata: Chunk IDs, Latency Metrics, Cosine Similarity scores, and Retrieval Traces.
- **Hierarchy as Authority:** We use a high-contrast scale. Large `headline-sm` titles for document names are paired immediately with tiny, monospaced `label-sm` metadata to create an "Industrial Ledger" look.

---

## 4. Elevation & Depth: Tonal Layering
Traditional drop shadows are largely banished in favor of physical layering.

- **The Layering Principle:** To highlight a "Retrieved Document" card, do not add a shadow. Instead, place a `surface-container-lowest` card on a `surface-container-high` track. The contrast in "cleanliness" creates the lift.
- **Ambient Shadows:** For floating modals, use a "Low-Albedo" shadow: `box-shadow: 0 12px 40px rgba(43, 52, 55, 0.06);`. This mimics the soft ambient occlusion found in a professional studio.
- **The "Ghost Border" Fallback:** Where separation is strictly required (e.g., in high-density data tables), use a `outline-variant` at 15% opacity. It should be felt, not seen.
- **Glassmorphism:** Navigation rails should use semi-transparent `surface` tokens with backdrop-blur. This allows the "trace" of the data behind it to bleed through, reinforcing the "Research Workspace" feel.

---

## 5. Components: Precision Primitives

### Evidence Cards (Bespoke Component)
The core of the RAG experience.
- **Body:** `surface-container-lowest` background.
- **Header:** A top-aligned bar using `label-sm` (monospaced) for the Source ID and a "Ghost Border" bottom edge.
- **Content:** `body-sm` for the text snippet.
- **Footer:** Right-aligned "Relevance Score" using `secondary` color tokens.

### Input Fields
- **Default State:** No background, only a bottom `outline-variant` (20% opacity) "Ghost Border."
- **Focus State:** Background shifts to `surface-container-lowest` with a 1px solid `primary` bottom border. No "glow" effects.

### Buttons
- **Primary:** `primary` background, `on-primary` text. `0.25rem` (sm) corner radius.
- **Tertiary (Technical):** Use `label-md` (monospaced) with `outline-variant` text. These should feel like "toggles" on a piece of hardware.

### Retrieval Traces (Bespoke Component)
A vertical "stepper" line using `outline-variant`. Each node is a tiny 4px square. Use `surface-dim` for inactive paths and `primary` for the "Winning Path" in a RAG retrieval chain.

---

## 6. Do's and Don'ts

### Do
- **Embrace Density:** It is okay to have a high amount of information on screen. Researchers prefer "Glanceability" over "White Space."
- **Use Monospace Accents:** Use `Space Grotesk` for any number or ID. It reinforces the engineering-heavy persona.
- **Align to a Rigorous Grid:** Use an 8px base grid, but allow for asymmetrical sidebars (e.g., a narrow left nav and a wide right evidence-inspector).

### Don't
- **No Rounded Corners > 8px:** High-radius "pill" buttons or cards destroy the "Professional Tool" aesthetic. Stick to `sm` (2px) or `md` (4px).
- **No Purple/Vibrant Gradients:** Stick strictly to the Graphite and Slate palette. Vibrancy is a distraction from data.
- **No Dividers:** If you find yourself adding a horizontal line, ask if you can use a background color shift or 24px of vertical space instead.