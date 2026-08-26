// Visual Animations Engine (Floating Leaves, Typewriter, Confetti, Count-Up Stats, Ripple)

// 1. Floating Leaves Canvas Particle Animation
export function initFloatingLeaves() {
  const canvas = document.getElementById('floating-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  
  let width = (canvas.width = window.innerWidth);
  let height = (canvas.height = window.innerHeight);

  window.addEventListener('resize', () => {
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
  });

  const leafCount = 18;
  const leaves = [];

  for (let i = 0; i < leafCount; i++) {
    leaves.push({
      x: Math.random() * width,
      y: Math.random() * height,
      size: Math.random() * 12 + 10,
      speedY: Math.random() * 0.8 + 0.3,
      speedX: Math.random() * 0.4 - 0.2,
      rotation: Math.random() * Math.PI * 2,
      rotSpeed: Math.random() * 0.02 - 0.01,
      color: Math.random() > 0.5 ? '#66BB6A' : '#43A047',
      opacity: Math.random() * 0.4 + 0.2
    });
  }

  function drawLeaf(leaf) {
    ctx.save();
    ctx.translate(leaf.x, leaf.y);
    ctx.rotate(leaf.rotation);
    ctx.globalAlpha = leaf.opacity;
    ctx.fillStyle = leaf.color;

    ctx.beginPath();
    ctx.moveTo(0, -leaf.size);
    ctx.quadraticCurveTo(leaf.size / 2, 0, 0, leaf.size);
    ctx.quadraticCurveTo(-leaf.size / 2, 0, 0, -leaf.size);
    ctx.fill();
    ctx.restore();
  }

  function render() {
    ctx.clearRect(0, 0, width, height);
    leaves.forEach(leaf => {
      leaf.y += leaf.speedY;
      leaf.x += Math.sin(leaf.y * 0.01) * 0.5;
      leaf.rotation += leaf.rotSpeed;

      if (leaf.y > height + 20) {
        leaf.y = -20;
        leaf.x = Math.random() * width;
      }
      drawLeaf(leaf);
    });
    requestAnimationFrame(render);
  }

  render();
}

// 2. Typewriter Effect for Hero Title (Singleton & Cleaned)
let typewriterTimeout = null;

export function initTypewriter(elementId, textArray) {
  const el = document.getElementById(elementId);
  if (!el) return;

  if (typewriterTimeout) {
    clearTimeout(typewriterTimeout);
    typewriterTimeout = null;
  }

  let textIdx = 0;
  let charIdx = 0;
  let isDeleting = false;

  function type() {
    const currentText = textArray[textIdx];
    
    if (isDeleting) {
      charIdx = Math.max(0, charIdx - 1);
      el.textContent = currentText.substring(0, charIdx);
    } else {
      charIdx = Math.min(currentText.length, charIdx + 1);
      el.textContent = currentText.substring(0, charIdx);
    }

    let speed = isDeleting ? 30 : 60;

    if (!isDeleting && charIdx === currentText.length) {
      speed = 2500; // Pause at end of complete phrase
      isDeleting = true;
    } else if (isDeleting && charIdx === 0) {
      isDeleting = false;
      textIdx = (textIdx + 1) % textArray.length;
      speed = 500; // Pause before starting next phrase
    }

    typewriterTimeout = setTimeout(type, speed);
  }

  type();
}

// 3. Canvas Confetti Particle Burst (Triggered on Healthy Scan)
export function triggerConfetti() {
  const canvas = document.getElementById('confetti-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');

  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;

  const count = 120;
  const particles = [];
  const colors = ['#2E7D32', '#66BB6A', '#43A047', '#4CAF50', '#FFC107', '#E8F5E9'];

  for (let i = 0; i < count; i++) {
    particles.push({
      x: window.innerWidth / 2,
      y: window.innerHeight / 2,
      vx: (Math.random() - 0.5) * 16,
      vy: (Math.random() - 0.7) * 16,
      size: Math.random() * 8 + 4,
      color: colors[Math.floor(Math.random() * colors.length)],
      opacity: 1,
      gravity: 0.3,
      rotation: Math.random() * Math.PI * 2
    });
  }

  function animate() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    let active = false;

    particles.forEach(p => {
      p.x += p.vx;
      p.y += p.vy;
      p.vy += p.gravity;
      p.opacity -= 0.015;

      if (p.opacity > 0) {
        active = true;
        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.rotate(p.rotation);
        ctx.globalAlpha = p.opacity;
        ctx.fillStyle = p.color;
        ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size);
        ctx.restore();
      }
    });

    if (active) {
      requestAnimationFrame(animate);
    } else {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
    }
  }

  animate();
}

// 4. Count-Up Stat Numbers
export function animateCountUp(elementId, targetValue, duration = 2000, suffix = '') {
  const el = document.getElementById(elementId);
  if (!el) return;

  let start = 0;
  const increment = targetValue / (duration / 16);

  const timer = setInterval(() => {
    start += increment;
    if (start >= targetValue) {
      el.textContent = targetValue.toLocaleString() + suffix;
      clearInterval(timer);
    } else {
      el.textContent = Math.floor(start).toLocaleString() + suffix;
    }
  }, 16);
}

// 5. Button Ripple Click Effect
export function initRippleEffect() {
  document.addEventListener('click', (e) => {
    const target = e.target.closest('.ripple');
    if (!target) return;

    const rect = target.getBoundingClientRect();
    const ripple = document.createElement('span');
    ripple.className = 'ripple-effect';
    ripple.style.width = ripple.style.height = `${Math.max(rect.width, rect.height)}px`;
    ripple.style.left = `${e.clientX - rect.left - rect.width / 2}px`;
    ripple.style.top = `${e.clientY - rect.top - rect.height / 2}px`;

    target.appendChild(ripple);
    setTimeout(() => ripple.remove(), 600);
  });
}
