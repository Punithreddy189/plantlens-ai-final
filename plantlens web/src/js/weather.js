// Live Geolocation & Open-Meteo Weather API Integration Engine

export const WeatherWidget = {
  weatherCache: null,

  async fetchLiveWeather() {
    if (this.weatherCache) return this.weatherCache;

    return new Promise((resolve) => {
      if ('geolocation' in navigator) {
        navigator.geolocation.getCurrentPosition(
          async (position) => {
            const { latitude, longitude } = position.coords;
            try {
              const res = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m`);
              if (res.ok) {
                const data = await res.json();
                const current = data.current || {};
                const temp = Math.round(current.temperature_2m || 28);
                const humidity = current.relative_humidity_2m || 62;
                const rain = current.precipitation || 0;
                const weatherCode = current.weather_code || 0;

                const condition = this.interpretWeatherCode(weatherCode);
                const wateringAdvice = temp > 30 ? '🔥 High heat detected! Water plants thoroughly early morning.' :
                                       rain > 0.5 ? '🌧️ Recent rainfall detected. Skip manual watering today.' :
                                       '💧 Optimal time for watering: Early morning before 9 AM.';

                this.weatherCache = {
                  temp: `${temp}°C`,
                  condition,
                  humidity: `${humidity}%`,
                  rainChance: rain > 0 ? `${Math.min(Math.round(rain * 20), 100)}%` : '15%',
                  wateringAdvice,
                  isLive: true
                };
                resolve(this.weatherCache);
                return;
              }
            } catch (err) {
              console.warn('Open-Meteo API fetch error:', err);
            }
            resolve(this.getDefaultWeather());
          },
          (err) => {
            console.info('Geolocation permission denied or unavailable. Using default location weather.');
            resolve(this.getDefaultWeather());
          },
          { timeout: 8000 }
        );
      } else {
        resolve(this.getDefaultWeather());
      }
    });
  },

  interpretWeatherCode(code) {
    if (code === 0) return 'Clear Sky ☀️';
    if (code >= 1 && code <= 3) return 'Partly Cloudy 🌤️';
    if (code >= 45 && code <= 48) return 'Foggy 🌫️';
    if (code >= 51 && code <= 67) return 'Light Rain 🌧️';
    if (code >= 80 && code <= 82) return 'Rain Showers 🌦️';
    if (code >= 95) return 'Thunderstorm ⛈️';
    return 'Partly Cloudy 🌤️';
  },

  getDefaultWeather() {
    return {
      temp: '28°C',
      condition: 'Partly Cloudy 🌤️',
      humidity: '62%',
      rainChance: '15%',
      wateringAdvice: 'Optimal time for watering: Early morning before 9 AM.',
      isLive: false
    };
  },

  async renderWidget(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    // Show initial skeleton or placeholder
    container.innerHTML = `
      <div class="weather-hero-card">
        <div style="display: flex; align-items: center; gap: 14px;">
          <span style="font-size: 2.2rem;">🌤️</span>
          <div>
            <div style="font-weight: 700; font-size: 1.2rem; color: var(--text-primary);">Detecting Live Location Weather...</div>
            <div style="font-size: 0.875rem; color: var(--text-secondary);">Connecting to Open-Meteo API...</div>
          </div>
        </div>
      </div>
    `;

    const data = await this.fetchLiveWeather();

    container.innerHTML = `
      <div class="weather-hero-card">
        <div style="display: flex; align-items: center; gap: 14px;">
          <span style="font-size: 2.2rem;">${data.condition.includes('Clear') ? '☀️' : data.condition.includes('Rain') ? '🌧️' : '🌤️'}</span>
          <div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="font-weight: 700; font-size: 1.2rem; color: var(--text-primary);">${data.temp} - ${data.condition}</span>
              ${data.isLive ? '<span class="badge badge-success" style="font-size: 0.75rem; padding: 2px 8px;">📍 Live GPS</span>' : ''}
            </div>
            <div style="font-size: 0.875rem; color: var(--text-secondary);">Humidity: ${data.humidity} | Rain Chance: ${data.rainChance}</div>
          </div>
        </div>
        <div style="text-align: right; font-size: 0.85rem; color: var(--primary-color); font-weight: 600; max-width: 280px;">
          ${data.wateringAdvice}
        </div>
      </div>
    `;
  }
};
