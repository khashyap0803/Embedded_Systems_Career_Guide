# Custom Views — UI Documentation

> **Package:** `com.example.embeddedsystemscareerguide.ui.custom`  
> **Files:** `GamePathItemDecoration.kt` (46 lines), `ParticleBackgroundView.kt` (96 lines)

---

## GamePathItemDecoration.kt (46 lines)

### Purpose
`RecyclerView.ItemDecoration` that draws connecting lines between stage nodes in the learning-path RecyclerView.

### How It Works
- Override: `onDraw(canvas, parent, state)`.
- Iterates through visible `ViewHolder` pairs.
- Draws vertical line from bottom-center of each item to top-center of the next.
- Two paint styles:
  - **Completed path:** Solid cyan/teal line.
  - **Incomplete path:** Dashed grey line.

### Design Decisions
- **ItemDecoration vs. custom View:** Lines are drawn beneath RecyclerView items via decoration, avoiding z-order issues and keeping the adapter untouched.

---

## ParticleBackgroundView.kt (96 lines)

### Purpose
Custom `View` rendering animated twinkling particles as a decorative background.

### Inner Data Class
- **`Particle(x, y, vx, vy, alpha, size)`** — Each particle's position, velocity, opacity, and radius.

### All Functions (4)

- **`startAnimation()`** — Clears and creates 20 particles with random positions/velocities. Starts infinite `ValueAnimator` → calls `updateParticles()` + `invalidate()` each frame.
- **`updateParticles()`** — Updates each particle's position (`x += vx`, `y += vy`). Wraps around screen edges. Varies alpha using `sin()` for twinkling effect.
- **`onDraw(canvas)`** — Draws filled circles at each particle's position with current alpha.
- **`onDetachedFromWindow()`** — Cancels the animator to prevent leaks.

### Key Properties

| Property | Type | Role |
|---|---|---|
| `particles` | `MutableList<Particle>` | Active particles |
| `animator` | `ValueAnimator?` | Animation driver |
| `paint` | `Paint` | Shared paint (indigo #4F46E5) |

### Design Decisions
- **ValueAnimator-based:** Lighter than `SurfaceView`, appropriate for decorative background.
- **Screen wrapping:** Particles wrap around edges instead of bouncing, creating a seamless flow.
