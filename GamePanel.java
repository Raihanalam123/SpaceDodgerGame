import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.io.*;
import javax.sound.sampled.*;

public class GamePanel extends JPanel implements ActionListener {
    private Timer timer;
    private int spaceshipY = 250;
    private int spaceshipVelocity = 0;
    private final int GRAVITY = 1;
    private final int SPACESHIP_X = 100;
    private final int SPACESHIP_WIDTH = 40;
    private final int SPACESHIP_HEIGHT = 40;
    private ArrayList<Rectangle> asteroids = new ArrayList<>();
    private Random rand = new Random();
    private int score = 0;
    private boolean gameOver = false;
    private int asteroidSpeed = 5;
    private int asteroidSpawnCounter = 0;
    private int highScore = 0;
    private final String HIGH_SCORE_FILE = "highscore.txt";
    private boolean paused = false;
    private int lives = 3;
    private Image spaceshipImg, asteroidImg;
    // Starfield
    private class Star {
        int x, y, speed;
        Star(int x, int y, int speed) { this.x = x; this.y = y; this.speed = speed; }
    }
    private ArrayList<Star> stars = new ArrayList<>();
    private void initStars() {
        stars.clear();
        for (int i = 0; i < 60; i++) {
            stars.add(new Star(rand.nextInt(800), rand.nextInt(600), 1 + rand.nextInt(3)));
        }
    }
    // Explosion
    private boolean showExplosion = false;
    private int explosionX, explosionY, explosionFrame = 0;
    private final int EXPLOSION_FRAMES = 15;
    // Game state
    private boolean started = false;
    private float gameOverAlpha = 0f;
    // Animated gradient state
    private float gradientPhase = 0f;

    public GamePanel() {
        setFocusable(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(800, 600));
        timer = new Timer(16, this); // ~60 FPS
        timer.start();
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!started && e.getKeyCode() == KeyEvent.VK_SPACE) {
                    started = true;
                    restartGame();
                } else if (gameOver && e.getKeyCode() == KeyEvent.VK_SPACE) {
                    started = true;
                    restartGame();
                } else if (started && !gameOver && e.getKeyCode() == KeyEvent.VK_UP) {
                    spaceshipVelocity = -10;
                } else if (e.getKeyCode() == KeyEvent.VK_P) {
                    paused = !paused;
                }
            }
        });
        loadHighScore();
        loadImages();
        initStars();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Animate gradient colors
        float t = (float) (0.5 + 0.5 * Math.sin(gradientPhase));
        Color topColor = blendColors(new Color(10, 10, 30), new Color(30, 0, 60), t);
        Color bottomColor = blendColors(new Color(0, 0, 40), new Color(0, 20, 60), t);
        Graphics2D g2d = (Graphics2D) g;
        GradientPaint gp = new GradientPaint(0, 0, topColor, 0, getHeight(), bottomColor);
        g2d.setPaint(gp);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        // Draw stars
        g.setColor(Color.WHITE);
        for (Star s : stars) {
            g.fillRect(s.x, s.y, 2, 2);
        }
        // Draw asteroids
        for (Rectangle asteroid : asteroids) {
            if (asteroidImg != null) {
                g.drawImage(asteroidImg, asteroid.x, asteroid.y, asteroid.width, asteroid.height, null);
            }
            if (asteroidImg == null) {
                g.setColor(Color.RED);
                g.fillRect(asteroid.x, asteroid.y, asteroid.width, asteroid.height);
            }
        }
        // Draw spaceship with tilt
        int tilt = Math.max(-20, Math.min(20, -spaceshipVelocity * 2));
        Graphics2D g2 = (Graphics2D) g.create();
        g2.rotate(Math.toRadians(tilt), SPACESHIP_X + SPACESHIP_WIDTH/2, spaceshipY + SPACESHIP_HEIGHT/2);
        if (spaceshipImg != null) {
            g2.drawImage(spaceshipImg, SPACESHIP_X, spaceshipY, SPACESHIP_WIDTH, SPACESHIP_HEIGHT, null);
        }
        if (spaceshipImg == null) {
            g2.setColor(Color.CYAN);
            g2.fillRect(SPACESHIP_X, spaceshipY, SPACESHIP_WIDTH, SPACESHIP_HEIGHT);
        }
        g2.dispose();
        // Draw explosion
        if (showExplosion) {
            g.setColor(Color.ORANGE);
            g.fillOval(explosionX-20, explosionY-20, 80, 80);
            g.setColor(Color.YELLOW);
            g.fillOval(explosionX, explosionY, 40, 40);
        }
        // Draw score, high score, lives
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("Score: " + score, 20, 40);
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.drawString("High Score: " + highScore, 20, 70);
        g.setColor(Color.GREEN);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.drawString("Lives: " + lives, 20, 100);
        // Draw pause
        if (paused) {
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.setColor(Color.ORANGE);
            g.drawString("Paused", 300, 300);
        }
        // Start screen
        if (!started) {
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.setColor(Color.CYAN);
            g.drawString("Space Dodger", 220, 220);
            g.setFont(new Font("Arial", Font.BOLD, 28));
            g.setColor(Color.WHITE);
            g.drawString("Press SPACE to Start", 250, 300);
        }
        // Animated Game Over
        if (gameOver) {
            int alpha = Math.min(255, (int)(255 * gameOverAlpha));
            g.setFont(new Font("Arial", Font.BOLD, 48));
            g.setColor(new Color(255,255,0,alpha));
            g.drawString("Game Over!", 250, 300);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.setColor(new Color(255,255,255,alpha));
            g.drawString("Press SPACE to restart", 260, 350);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Animate gradient phase
        gradientPhase += 0.01f;
        if (gradientPhase > Math.PI * 2) gradientPhase -= Math.PI * 2;
        // Move stars
        for (Star s : stars) {
            s.x -= s.speed;
            if (s.x < 0) {
                s.x = getWidth();
                s.y = rand.nextInt(getHeight());
                s.speed = 1 + rand.nextInt(3);
            }
        }
        // Only run game logic if started and not gameOver
        if (!started) {
            repaint();
            return;
        }
        if (!gameOver && !paused) {
            // Update spaceship position
            spaceshipY += spaceshipVelocity;
            spaceshipVelocity += GRAVITY;
            if (spaceshipY < 0) spaceshipY = 0;
            if (spaceshipY > getHeight() - SPACESHIP_HEIGHT) spaceshipY = getHeight() - SPACESHIP_HEIGHT;
            // Spawn asteroids
            asteroidSpawnCounter++;
            int spawnRate = Math.max(20, 50 - score / 10); // Increase difficulty
            if (asteroidSpawnCounter > spawnRate || asteroids.isEmpty()) { // Ensure at least one asteroid at start
                int asteroidHeight = 60 + rand.nextInt(80);
                int asteroidY = rand.nextInt(getHeight() - asteroidHeight);
                asteroids.add(new Rectangle(getWidth(), asteroidY, 40, asteroidHeight));
                asteroidSpawnCounter = 0;
            }
            // Move asteroids and check for collisions
            Iterator<Rectangle> it = asteroids.iterator();
            while (it.hasNext()) {
                Rectangle asteroid = it.next();
                asteroid.x -= asteroidSpeed + score / 20; // Increase speed with score
                if (asteroid.x + asteroid.width < 0) {
                    it.remove();
                    score++;
                    playSound("score.wav");
                } else if (asteroid.intersects(new Rectangle(SPACESHIP_X, spaceshipY, SPACESHIP_WIDTH, SPACESHIP_HEIGHT))) {
                    it.remove();
                    lives--;
                    playSound("hit.wav");
                    showExplosion = true;
                    explosionX = SPACESHIP_X;
                    explosionY = spaceshipY;
                    explosionFrame = 0;
                    if (lives <= 0) {
                        gameOver = true;
                        playSound("gameover.wav");
                    }
                }
            }
            // Explosion animation
            if (showExplosion) {
                explosionFrame++;
                if (explosionFrame > EXPLOSION_FRAMES) showExplosion = false;
            }
        } else if (gameOver) {
            if (score > highScore) {
                highScore = score;
                saveHighScore();
            }
            if (gameOverAlpha < 1f) gameOverAlpha += 0.03f;
        }
        repaint();
    }

    private void restartGame() {
        spaceshipY = 250;
        spaceshipVelocity = 0;
        asteroids.clear();
        score = 0;
        gameOver = false;
        lives = 3;
        paused = false;
        // started is NOT reset here
        gameOverAlpha = 0f;
        showExplosion = false;
        initStars();
    }

    private void loadHighScore() {
        try (BufferedReader reader = new BufferedReader(new FileReader(HIGH_SCORE_FILE))) {
            String line = reader.readLine();
            if (line != null) {
                highScore = Integer.parseInt(line.trim());
            }
        } catch (IOException | NumberFormatException e) {
            highScore = 0;
        }
    }

    private void saveHighScore() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(HIGH_SCORE_FILE))) {
            writer.write(String.valueOf(highScore));
        } catch (IOException e) {
            // Ignore for now
        }
    }

    private void loadImages() {
        try {
            spaceshipImg = Toolkit.getDefaultToolkit().getImage("spaceship.png");
            asteroidImg = Toolkit.getDefaultToolkit().getImage("asteroid.png");
        } catch (Exception e) {
            spaceshipImg = null;
            asteroidImg = null;
        }
    }

    private void playSound(String soundFile) {
        try {
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(new File(soundFile));
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
        } catch (Exception e) {
            // Ignore sound errors
        }
    }

    // Helper to blend two colors
    private Color blendColors(Color c1, Color c2, float t) {
        int r = (int) (c1.getRed() * (1 - t) + c2.getRed() * t);
        int g = (int) (c1.getGreen() * (1 - t) + c2.getGreen() * t);
        int b = (int) (c1.getBlue() * (1 - t) + c2.getBlue() * t);
        return new Color(r, g, b);
    }
} 