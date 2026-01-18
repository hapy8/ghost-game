import pygame
import random
import sys
import os
import json
import math
import numpy as np
from enum import Enum

# Initialize Pygame
pygame.init()
pygame.mixer.init(frequency=44100, size=-16, channels=2, buffer=512)

# Constants
SCREEN_WIDTH = 1280
SCREEN_HEIGHT = 720
FPS = 60

# Colors
WHITE = (255, 255, 255)
BLACK = (0, 0, 0)
BLUE = (100, 150, 255)
DARK_BLUE = (20, 30, 60)
GREEN = (50, 200, 50)
RED = (255, 60, 60)
PURPLE = (160, 80, 220)
GOLD = (255, 215, 0)
GRAY = (128, 128, 128)
GHOST_COLOR = (220, 220, 255)
GLOW_COLOR = (255, 255, 150)

# Game States
class GameState(Enum):
    MENU = 1
    PLAYING = 2
    GAME_OVER = 3
    PAUSED = 4

class Synthesizer:
    def __init__(self):
        self.sample_rate = 44100
        
    def generate_wave(self, func, duration, frequency, volume=0.5):
        n_samples = int(self.sample_rate * duration)
        t = np.linspace(0, duration, n_samples, False)
        wave = func(t, frequency) * volume
        # Apply simple fade out
        envelope = np.exp(-3 * t)
        wave = wave * envelope
        
        # Stereo duplication and contiguous check
        stereo = np.column_stack((wave, wave))
        
        # Convert to 16-bit integer
        audio = (stereo * 32767).astype(np.int16)
        return pygame.sndarray.make_sound(audio)

    def generate_jump_sound(self):
        duration = 0.3
        n_samples = int(self.sample_rate * duration)
        t = np.linspace(0, duration, n_samples, False)
        # Sliding frequency 300 -> 600
        freq = np.linspace(300, 600, n_samples)
        wave = np.sin(2 * np.pi * freq * t) * 0.3
        
        stereo = np.column_stack((wave, wave))
        audio = (stereo * 32767).astype(np.int16)
        return pygame.sndarray.make_sound(audio)

    def generate_collect_sound(self):
        # High ping
        duration = 0.15
        n_samples = int(self.sample_rate * duration)
        t = np.linspace(0, duration, n_samples, False)
        wave = np.sin(2 * np.pi * 1200 * t) * 0.2
        # Sharp fade
        envelope = np.linspace(1, 0, n_samples)
        wave = wave * envelope
        
        stereo = np.column_stack((wave, wave))
        audio = (stereo * 32767).astype(np.int16)
        return pygame.sndarray.make_sound(audio)

    def generate_music_loop(self):
        # Simple ambient arpeggio
        bpm = 120
        duration_per_beat = 60 / bpm
        total_beats = 8
        total_duration = duration_per_beat * total_beats
        n_samples = int(self.sample_rate * total_duration)
        full_buffer = np.zeros(n_samples, dtype=np.float32)
        
        notes = [220, 261, 329, 392, 440, 392, 329, 261] # A minor ish
        
        beat_samples = int(self.sample_rate * duration_per_beat)
        
        for i, freq in enumerate(notes):
            t = np.linspace(0, duration_per_beat, beat_samples, False)
            wave = np.sin(2 * np.pi * freq * t) * 0.1
            # Envelope
            env = np.linspace(1, 0, beat_samples)
            
            start = i * beat_samples
            end = start + beat_samples
            full_buffer[start:end] += wave * env
            
            # Add a bass note every 4 beats
            if i % 4 == 0:
                bass_wave = np.sin(2 * np.pi * (freq/2) * t) * 0.15
                full_buffer[start:end] += bass_wave
        
        stereo = np.column_stack((full_buffer, full_buffer))
        audio = (stereo * 32767).astype(np.int16)
        return pygame.sndarray.make_sound(audio)

class Particle:
    def __init__(self, x, y, color, speed, size, life):
        self.x = x
        self.y = y
        self.color = color
        self.vx = random.uniform(-speed, speed)
        self.vy = random.uniform(-speed, speed)
        self.size = size
        self.life = life
        self.original_life = life

    def update(self):
        self.x += self.vx
        self.y += self.vy
        self.life -= 1
        self.size = max(0, self.size * 0.95)

    def draw(self, screen):
        if self.life > 0:
            alpha = int((self.life / self.original_life) * 255)
            s = pygame.Surface((int(self.size * 2), int(self.size * 2)), pygame.SRCALPHA)
            pygame.draw.circle(s, (*self.color, alpha), (int(self.size), int(self.size)), int(self.size))
            screen.blit(s, (int(self.x - self.size), int(self.y - self.size)))

class ParticleSystem:
    def __init__(self):
        self.particles = []

    def emit(self, x, y, color, count=10, speed=2, size=5, life=30):
        for _ in range(count):
            self.particles.append(Particle(x, y, color, speed, size, life))

    def update(self):
        self.particles = [p for p in self.particles if p.life > 0]
        for p in self.particles:
            p.update()

    def draw(self, screen):
        for p in self.particles:
            p.draw(screen)

class Button:
    def __init__(self, x, y, width, height, text, color, hover_color, action=None, font_size=40):
        self.rect = pygame.Rect(x, y, width, height)
        self.text = text
        self.color = color
        self.hover_color = hover_color
        self.action = action
        self.font = pygame.font.Font(None, font_size)
        self.is_hovered = False

    def update(self, mouse_pos):
        self.is_hovered = self.rect.collidepoint(mouse_pos)

    def draw(self, screen):
        color = self.hover_color if self.is_hovered else self.color
        # Draw shadow
        pygame.draw.rect(screen, (0, 0, 0, 100), (self.rect.x + 4, self.rect.y + 4, self.rect.width, self.rect.height), border_radius=12)
        # Draw button
        pygame.draw.rect(screen, color, self.rect, border_radius=12)
        pygame.draw.rect(screen, WHITE, self.rect, 2, border_radius=12)
        
        text_surf = self.font.render(self.text, True, WHITE)
        text_rect = text_surf.get_rect(center=self.rect.center)
        screen.blit(text_surf, text_rect)

    def handle_event(self, event):
        if event.type == pygame.MOUSEBUTTONDOWN:
            if event.button == 1 and self.is_hovered:
                if self.action:
                    self.action()

class Ghost:
    def __init__(self):
        self.x = 150
        self.y = SCREEN_HEIGHT // 2
        self.width = 44
        self.height = 44
        self.vel_y = 0
        self.jump_power = -22
        self.gravity = 1.2
        self.on_ground = False
        self.float_offset = 0
        self.float_speed = 0.15
        self.rect = pygame.Rect(self.x, self.y, self.width, self.height)
        
    def update(self):
        if not self.on_ground:
            self.vel_y += self.gravity
        
        self.y += self.vel_y
        
        ground_level = SCREEN_HEIGHT - 100
        if self.y + self.height >= ground_level:
            self.y = ground_level - self.height
            self.vel_y = 0
            self.on_ground = True
        else:
            self.on_ground = False
        
        self.float_offset += self.float_speed
        self.rect.y = int(self.y)

    def jump(self):
        if self.on_ground:
            self.vel_y = self.jump_power
            self.on_ground = False
            return True
        return False
    
    def draw(self, screen):
        draw_y = self.y + math.sin(self.float_offset) * 5
        
        # Ghost Body (Glow)
        s = pygame.Surface((80, 80), pygame.SRCALPHA)
        pygame.draw.circle(s, (*GHOST_COLOR, 80), (40, 40), 30)
        screen.blit(s, (self.x - 18, draw_y - 12))
        
        # Main Body
        head_rect = pygame.Rect(self.x, draw_y, self.width, self.height - 10)
        pygame.draw.circle(screen, GHOST_COLOR, (int(self.x + self.width // 2), int(draw_y + self.width // 2)), self.width // 2)
        
        # Tail
        tail_points = []
        for i in range(5):
            tx = self.x + (i * self.width // 4)
            ty = draw_y + self.height - 5 + math.sin(self.float_offset * 0.5 + i) * 8
            tail_points.append((tx, ty))
        
        # Draw tail polygon
        points = [(self.x, draw_y + self.width//2)] + tail_points + [(self.x + self.width, draw_y + self.width//2)]
        if len(points) > 2:
            pygame.draw.polygon(screen, GHOST_COLOR, points)
            
        # Face
        eye_y = draw_y + 15
        pygame.draw.ellipse(screen, BLACK, (self.x + 8, eye_y, 10, 14))
        pygame.draw.ellipse(screen, BLACK, (self.x + 28, eye_y, 10, 14))
        pygame.draw.circle(screen, WHITE, (self.x + 10, int(eye_y + 4)), 3)
        pygame.draw.circle(screen, WHITE, (self.x + 30, int(eye_y + 4)), 3)

class Obstacle:
    def __init__(self, x, obstacle_type, speed_multiplier=1.0):
        self.x = x
        self.type = obstacle_type
        self.speed = 8 * speed_multiplier
        self.passed = False
        
        if obstacle_type == "tree":
            self.width = 40
            self.height = 90
            self.y = SCREEN_HEIGHT - 100 - self.height
            self.color = GREEN
        elif obstacle_type == "rock":
            self.width = 50
            self.height = 40
            self.y = SCREEN_HEIGHT - 100 - self.height
            self.color = GRAY
        elif obstacle_type == "bat":
            self.width = 40
            self.height = 30
            self.y = SCREEN_HEIGHT - 220
            self.color = PURPLE
            self.wing_flap = 0

    def update(self):
        self.x -= self.speed
        if self.type == "bat":
            self.wing_flap += 0.4
    
    def draw(self, screen):
        if self.type == "tree":
            pygame.draw.rect(screen, (80, 50, 20), (self.x + 12, self.y + 40, 16, 50))
            pygame.draw.polygon(screen, self.color, [
                (self.x + 20, self.y),
                (self.x, self.y + 60),
                (self.x + 40, self.y + 60)
            ])
            pygame.draw.polygon(screen, (40, 180, 40), [
                 (self.x + 20, self.y - 20),
                 (self.x + 5, self.y + 30),
                 (self.x + 35, self.y + 30)
            ])
        elif self.type == "rock":
            pygame.draw.circle(screen, self.color, (int(self.x + 25), int(self.y + 20)), 20)
            pygame.draw.circle(screen, (100, 100, 100), (int(self.x + 15), int(self.y + 15)), 8)
        elif self.type == "bat":
            body_y = self.y + math.sin(self.wing_flap) * 10
            wing_y = body_y - math.cos(self.wing_flap) * 15
            pygame.draw.polygon(screen, self.color, [
                (self.x + 20, body_y), (self.x - 5, wing_y), (self.x + 15, body_y + 10) 
            ])
            pygame.draw.polygon(screen, self.color, [
                (self.x + 20, body_y), (self.x + 45, wing_y), (self.x + 25, body_y + 10)
            ])
            pygame.draw.circle(screen, (50, 20, 50), (int(self.x + 20), int(body_y)), 10)
            pygame.draw.circle(screen, RED, (int(self.x + 17), int(body_y - 2)), 2)
            pygame.draw.circle(screen, RED, (int(self.x + 23), int(body_y - 2)), 2)

    def get_rect(self):
        return pygame.Rect(self.x, self.y, self.width, self.height)

class Collectible:
    def __init__(self, x, speed_multiplier):
        self.x = x
        self.y = random.randint(300, SCREEN_HEIGHT - 200)
        self.width = 25
        self.height = 25
        self.speed = 5 * speed_multiplier
        self.collected = False
        self.glow_timer = 0
        
    def update(self):
        self.x -= self.speed
        self.glow_timer += 0.1
        
    def draw(self, screen):
        if not self.collected:
            scale = 1.0 + math.sin(self.glow_timer) * 0.1
            size = int(12 * scale)
            s = pygame.Surface((50, 50), pygame.SRCALPHA)
            pygame.draw.circle(s, (255, 255, 100, 100), (25, 25), size + 8)
            screen.blit(s, (self.x - 12, self.y - 12))
            pygame.draw.circle(screen, GOLD, (int(self.x + 12), int(self.y + 12)), size)
            pygame.draw.circle(screen, WHITE, (int(self.x + 12), int(self.y + 12)), size // 2)
    
    def get_rect(self):
        return pygame.Rect(self.x, self.y, self.width, self.height)

class Game:
    def __init__(self):
        self.screen = pygame.display.set_mode((SCREEN_WIDTH, SCREEN_HEIGHT))
        pygame.display.set_caption("Ghost Run - Next Gen with Audio")
        self.clock = pygame.time.Clock()
        self.font = pygame.font.Font(None, 36)
        self.big_font = pygame.font.Font(None, 80)
        
        self.state = GameState.MENU
        self.particles = ParticleSystem()
        self.load_highscore()
        
        # Audio Setup
        self.synth = Synthesizer()
        self.is_muted = False
        try:
            self.jump_sfx = self.synth.generate_jump_sound()
            self.collect_sfx = self.synth.generate_collect_sound()
            self.music_loop = self.synth.generate_music_loop()
            self.has_audio = True
        except Exception as e:
            print(f"Audio generation failed: {e}")
            self.has_audio = False
            
        # Try to load custom music if exists
        if os.path.exists("music.mp3"):
            try:
                pygame.mixer.music.load("music.mp3")
                self.using_custom_music = True
            except:
                self.using_custom_music = False
        else:
            self.using_custom_music = False
        
        if self.has_audio and not self.using_custom_music:
            self.music_channel = pygame.mixer.Channel(0)
        
        # UI
        self.start_btn = Button(SCREEN_WIDTH//2 - 100, SCREEN_HEIGHT//2 + 50, 200, 60, "START", BLUE, GREEN, self.start_game)
        self.quit_btn = Button(SCREEN_WIDTH//2 - 100, SCREEN_HEIGHT//2 + 130, 200, 60, "QUIT", RED, (200, 50, 50), self.quit_game)
        self.restart_btn = Button(SCREEN_WIDTH//2 - 120, SCREEN_HEIGHT//2 + 50, 240, 60, "PLAY AGAIN", BLUE, GREEN, self.start_game)
        self.menu_btn = Button(SCREEN_WIDTH//2 - 120, SCREEN_HEIGHT//2 + 130, 240, 60, "MAIN MENU", GRAY, (150, 150, 150), self.to_menu)
        
        # Pause UI
        self.resume_btn = Button(SCREEN_WIDTH//2 - 120, SCREEN_HEIGHT//2 - 40, 240, 60, "RESUME", BLUE, GREEN, self.resume_game)
        self.pause_menu_btn = Button(SCREEN_WIDTH//2 - 120, SCREEN_HEIGHT//2 + 40, 240, 60, "MAIN MENU", GRAY, (150, 150, 150), self.to_menu)
        
        # HUD Buttons (Toggle Pause & Mute)
        self.toggle_pause_btn = Button(SCREEN_WIDTH - 60, 10, 50, 50, "||", DARK_BLUE, BLUE, self.toggle_pause, font_size=30)
        self.mute_btn = Button(SCREEN_WIDTH - 120, 10, 50, 50, "VOL", DARK_BLUE, BLUE, self.toggle_mute, font_size=24)

        self.reset_game_logic()

    def load_highscore(self):
        try:
            with open("highscore.json", "r") as f:
                data = json.load(f)
                self.high_score = data.get("highscore", 0)
        except:
            self.high_score = 0

    def save_highscore(self):
        if self.score > self.high_score:
            self.high_score = self.score
            with open("highscore.json", "w") as f:
                json.dump({"highscore": self.high_score}, f)

    def reset_game_logic(self):
        self.ghost = Ghost()
        self.obstacles = []
        self.collectibles = []
        self.score = 0
        self.obstacle_timer = 0
        self.collectible_timer = 0
        self.background_x = 0
        self.difficulty_multiplier = 1.0

    def start_game(self):
        self.reset_game_logic()
        self.state = GameState.PLAYING
        # Play Music if not muted
        if not self.is_muted:
            if self.using_custom_music:
                pygame.mixer.music.play(-1)
            elif self.has_audio:
                if not self.music_channel.get_busy():
                    self.music_channel.play(self.music_loop, loops=-1)
                
    def resume_game(self):
        self.state = GameState.PLAYING
        if not self.is_muted:
            if self.has_audio and not self.using_custom_music:
                self.music_channel.unpause()
            elif self.using_custom_music:
                pygame.mixer.music.unpause()
            
    def pause_game(self):
        self.state = GameState.PAUSED
        if self.has_audio and not self.using_custom_music:
            self.music_channel.pause()
        elif self.using_custom_music:
            pygame.mixer.music.pause()
            
    def toggle_pause(self):
        if self.state == GameState.PLAYING:
            self.pause_game()
        elif self.state == GameState.PAUSED:
            self.resume_game()
            
    def toggle_mute(self):
        self.is_muted = not self.is_muted
        
        if self.is_muted:
            self.mute_btn.text = "MUTE"
            self.mute_btn.color = (150, 150, 150) # Greyed out
            # Silence everything
            if self.using_custom_music:
                pygame.mixer.music.set_volume(0)
            elif self.has_audio:
                self.music_channel.set_volume(0)
                self.jump_sfx.set_volume(0)
                self.collect_sfx.set_volume(0)
        else:
            self.mute_btn.text = "VOL"
            self.mute_btn.color = DARK_BLUE # Normal color
            # Restore volume
            if self.using_custom_music:
                pygame.mixer.music.set_volume(1.0)
            elif self.has_audio:
                self.music_channel.set_volume(1.0)
                self.jump_sfx.set_volume(1.0)
                self.collect_sfx.set_volume(1.0)
                # Restart music if it was stopped/not playing
                if not self.music_channel.get_busy() and self.state == GameState.PLAYING:
                     self.music_channel.play(self.music_loop, loops=-1)


    def quit_game(self):
        pygame.quit()
        sys.exit()

    def to_menu(self):
        self.state = GameState.MENU
        if self.using_custom_music:
            pygame.mixer.music.stop()
        elif self.has_audio:
            self.music_channel.stop()

    def spawn_obstacle(self):
        obstacle_types = ["tree", "rock", "bat"]
        if self.score > 500:
            weights = [30, 30, 40]
        else:
            weights = [40, 40, 20]
        obstacle_type = random.choices(obstacle_types, weights=weights, k=1)[0]
        self.obstacles.append(Obstacle(SCREEN_WIDTH, obstacle_type, self.difficulty_multiplier))

    def update_menu(self):
        mouse_pos = pygame.mouse.get_pos()
        self.start_btn.update(mouse_pos)
        self.quit_btn.update(mouse_pos)
        self.background_x -= 1
        if self.background_x <= -SCREEN_WIDTH:
            self.background_x = 0

    def update_playing(self):
        mouse_pos = pygame.mouse.get_pos()
        self.toggle_pause_btn.update(mouse_pos)
        self.toggle_pause_btn.text = "||"
        self.mute_btn.update(mouse_pos)
        
        self.ghost.update()
        self.difficulty_multiplier = 1.0 + (self.score / 2000.0)
        
        for obstacle in self.obstacles[:]:
            obstacle.update()
            if obstacle.x + obstacle.width < 0:
                self.obstacles.remove(obstacle)
                self.score += 10
        
        for collectible in self.collectibles[:]:
            collectible.update()
            if collectible.x + collectible.width < 0:
                self.collectibles.remove(collectible)
        
        self.obstacle_timer += 1
        spawn_threshold = max(40, 100 - int(self.score / 50))
        if self.obstacle_timer > random.randint(spawn_threshold, spawn_threshold + 60):
            self.spawn_obstacle()
            self.obstacle_timer = 0
            
        self.collectible_timer += 1
        if self.collectible_timer > random.randint(180, 300):
            self.collectibles.append(Collectible(SCREEN_WIDTH, self.difficulty_multiplier))
            self.collectible_timer = 0
            
        ghost_rect = self.ghost.rect
        for obstacle in self.obstacles:
            if ghost_rect.colliderect(obstacle.get_rect()):
                self.particles.emit(self.ghost.x, self.ghost.y, GHOST_COLOR, count=20, speed=5)
                self.particles.emit(obstacle.x, obstacle.y, obstacle.color, count=10, speed=3)
                self.save_highscore()
                self.state = GameState.GAME_OVER
                if self.using_custom_music:
                    pygame.mixer.music.stop()
                elif self.has_audio:
                    self.music_channel.stop()
                
        for collectible in self.collectibles[:]:
            if not collectible.collected and ghost_rect.colliderect(collectible.get_rect()):
                collectible.collected = True
                self.collectibles.remove(collectible)
                self.score += 50
                self.particles.emit(collectible.x, collectible.y, GOLD, count=15, speed=4)
                if self.has_audio:
                    self.collect_sfx.play()

        self.background_x -= 2 * self.difficulty_multiplier
        if self.background_x <= -SCREEN_WIDTH:
            self.background_x = 0
            
        self.particles.update()

    def update_paused(self):
        mouse_pos = pygame.mouse.get_pos()
        self.resume_btn.update(mouse_pos)
        self.pause_menu_btn.update(mouse_pos)
        self.toggle_pause_btn.update(mouse_pos)
        self.toggle_pause_btn.text = ">"
        self.mute_btn.update(mouse_pos)

    def update_game_over(self):
        mouse_pos = pygame.mouse.get_pos()
        self.restart_btn.update(mouse_pos)
        self.menu_btn.update(mouse_pos)
        self.particles.update()

    def draw_background(self):
        for y in range(SCREEN_HEIGHT):
            r = max(0, min(255, 20 + y // 5))
            g = max(0, min(255, 20 + y // 6))
            b = max(0, min(255, 50 + y // 4))
            pygame.draw.line(self.screen, (r, g, b), (0, y), (SCREEN_WIDTH, y))
        pygame.draw.rect(self.screen, (20, 60, 20), (0, SCREEN_HEIGHT - 100, SCREEN_WIDTH, 100))
        pygame.draw.line(self.screen, (40, 100, 40), (0, SCREEN_HEIGHT - 100), (SCREEN_WIDTH, SCREEN_HEIGHT - 100), 4)

        for i in range(5):
            cloud_x = (self.background_x * 0.5 + i * 400) % (SCREEN_WIDTH + 200) - 100
            cloud_y = 100 + i * 40 + math.sin(self.obstacle_timer * 0.01 + i) * 20
            pygame.draw.ellipse(self.screen, (255, 255, 255, 200), (cloud_x, cloud_y, 100, 50))

    def draw_hud(self):
        score_surf = self.font.render(f"Score: {self.score}", True, WHITE)
        self.screen.blit(score_surf, (20, 20))
        hi_surf = self.font.render(f"HI: {self.high_score}", True, GOLD)
        self.screen.blit(hi_surf, (20, 60))
        
        # Draw Buttons
        self.toggle_pause_btn.draw(self.screen)
        self.mute_btn.draw(self.screen)

    def draw(self):
        self.draw_background()
        
        if self.state == GameState.MENU:
            title = self.big_font.render("GHOST RUN", True, WHITE)
            shadow = self.big_font.render("GHOST RUN", True, BLACK)
            t_rect = title.get_rect(center=(SCREEN_WIDTH//2, SCREEN_HEIGHT//3))
            self.screen.blit(shadow, (t_rect.x + 4, t_rect.y + 4))
            self.screen.blit(title, t_rect)
            pygame.draw.circle(self.screen, GHOST_COLOR, (SCREEN_WIDTH//2, SCREEN_HEIGHT//2 - 50), 30)
            self.start_btn.draw(self.screen)
            self.quit_btn.draw(self.screen)
            
        elif self.state == GameState.PLAYING:
            self.ghost.draw(self.screen)
            for obstacle in self.obstacles:
                obstacle.draw(self.screen)
            for collectible in self.collectibles:
                collectible.draw(self.screen)
            self.particles.draw(self.screen)
            self.draw_hud()
        
        elif self.state == GameState.PAUSED:
            # Draw game elements frozen in background
            self.ghost.draw(self.screen)
            for obstacle in self.obstacles:
                obstacle.draw(self.screen)
            for collectible in self.collectibles:
                collectible.draw(self.screen)
            self.draw_hud() 
            
            # Semi-transparent overlay
            overlay = pygame.Surface((SCREEN_WIDTH, SCREEN_HEIGHT))
            overlay.set_alpha(128)
            overlay.fill(BLACK)
            self.screen.blit(overlay, (0, 0))
            
            # Pause Text
            pause_text = self.big_font.render("PAUSED", True, WHITE)
            text_rect = pause_text.get_rect(center=(SCREEN_WIDTH//2, SCREEN_HEIGHT//3 - 50))
            self.screen.blit(pause_text, text_rect)
            
            self.resume_btn.draw(self.screen)
            self.pause_menu_btn.draw(self.screen)
            
            # Re-draw toggle buttons on top of overlay
            self.toggle_pause_btn.draw(self.screen)
            self.mute_btn.draw(self.screen)
            
        elif self.state == GameState.GAME_OVER:
            self.ghost.draw(self.screen)
            for obstacle in self.obstacles:
                obstacle.draw(self.screen)
            overlay = pygame.Surface((SCREEN_WIDTH, SCREEN_HEIGHT))
            overlay.set_alpha(180)
            overlay.fill(BLACK)
            self.screen.blit(overlay, (0, 0))
            over_text = self.big_font.render("GAME OVER", True, RED)
            score_text = self.font.render(f"Final Score: {self.score}", True, WHITE)
            over_rect = over_text.get_rect(center=(SCREEN_WIDTH//2, SCREEN_HEIGHT//3))
            score_rect = score_text.get_rect(center=(SCREEN_WIDTH//2, SCREEN_HEIGHT//3 + 70))
            self.screen.blit(over_text, over_rect)
            self.screen.blit(score_text, score_rect)
            self.restart_btn.draw(self.screen)
            self.menu_btn.draw(self.screen)
            
        pygame.display.flip()

    def run(self):
        running = True
        while running:
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    running = False
                
                if self.state == GameState.MENU:
                    self.start_btn.handle_event(event)
                    self.quit_btn.handle_event(event)
                    
                elif self.state == GameState.PLAYING:
                    self.toggle_pause_btn.handle_event(event)
                    self.mute_btn.handle_event(event)
                    if event.type == pygame.KEYDOWN:
                        if event.key == pygame.K_SPACE or event.key == pygame.K_UP:
                            if self.ghost.jump():
                                self.particles.emit(self.ghost.x + 10, self.ghost.y + 40, WHITE, count=5, speed=2)
                                if self.has_audio:
                                    self.jump_sfx.play()
                        if event.key == pygame.K_ESCAPE:
                            self.pause_game()
                                
                elif self.state == GameState.PAUSED:
                    self.resume_btn.handle_event(event)
                    self.pause_menu_btn.handle_event(event)
                    self.toggle_pause_btn.handle_event(event)
                    self.mute_btn.handle_event(event)
                    if event.type == pygame.KEYDOWN:
                        if event.key == pygame.K_ESCAPE:
                            self.resume_game()
                            
                elif self.state == GameState.GAME_OVER:
                    self.restart_btn.handle_event(event)
                    self.menu_btn.handle_event(event)

            if self.state == GameState.MENU:
                self.update_menu()
            elif self.state == GameState.PLAYING:
                self.update_playing()
            elif self.state == GameState.PAUSED:
                self.update_paused()
            elif self.state == GameState.GAME_OVER:
                self.update_game_over()
            
            self.draw()
            self.clock.tick(FPS)
        
        pygame.quit()

def run_game():
    game = Game()
    game.run()

if __name__ == "__main__":
    run_game()