// Garden Analytics Calculator Module
import { StorageManager } from './storage.js';

export const GardenAnalytics = {
  calculateMetrics() {
    const garden = StorageManager.getGarden();
    const total = garden.length;

    if (total === 0) {
      return {
        total: 0,
        healthyPercent: '0%',
        diseasedPercent: '0%',
        todaysWaterCount: 0,
        weeklyGrowth: '+0%',
        averageHealth: '0 / 100'
      };
    }

    const healthyCount = garden.filter(p => p.healthStatus === 'healthy').length;
    const diseasedCount = total - healthyCount;

    const healthyPct = Math.round((healthyCount / total) * 100);
    const diseasedPct = 100 - healthyPct;

    const totalHealthScoreSum = garden.reduce((sum, p) => sum + (p.healthScore || 80), 0);
    const avgHealth = Math.round(totalHealthScoreSum / total);

    return {
      total,
      healthyCount,
      diseasedCount,
      healthyPercent: `${healthyPct}%`,
      diseasedPercent: `${diseasedPct}%`,
      todaysWaterCount: Math.ceil(total * 0.4),
      weeklyGrowth: '+12%',
      averageHealth: `${avgHealth} / 100`
    };
  }
};
