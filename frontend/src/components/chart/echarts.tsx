import ReactEChartsCore from 'echarts-for-react/esm/core';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ChartProps } from './Chart';

// Trimmed ECharts: only the chart types the app uses (ui-kit § Charts → Bundle). Loaded lazily by <Chart>.
echarts.use([LineChart, BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

const css = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

/** The chart's look, set once (ui-kit § Charts → Style); colours come from the token block. */
function buildOption({ kind, categories, series, valueSuffix, integer }: ChartProps) {
  const palette = [1, 2, 3, 4, 5].map((i) => css(`--chart-${i}`));
  const muted = css('--muted-foreground');
  const ink = css('--foreground');
  const rule = css('--border');
  const card = css('--card');
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  const font = css('--font-body') || 'sans-serif';
  const label = { color: muted, fontSize: 12, fontFamily: font };
  const tooltip = {
    backgroundColor: card,
    borderColor: rule,
    borderWidth: 1,
    padding: [8, 10],
    textStyle: { color: ink, fontFamily: font, fontSize: 12 },
    extraCssText: 'border-radius:10px;box-shadow:0 8px 24px -12px rgba(40,32,20,.25);',
    valueFormatter: (v: unknown) => (v == null ? '—' : `${v}${valueSuffix ?? ''}`),
  };
  const base = {
    color: palette,
    animation: !reduced,
    animationDuration: 700,
    animationEasing: 'cubicOut' as const,
    textStyle: { fontFamily: font },
    legend: series.length > 1 || kind === 'donut' ? { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { ...label, color: ink } } : undefined,
  };

  if (kind === 'donut') {
    return {
      ...base,
      tooltip: { ...tooltip, trigger: 'item' },
      series: [
        {
          type: 'pie',
          radius: ['55%', '78%'],
          top: 0,
          bottom: 28,
          itemStyle: { borderColor: card, borderWidth: 2 },
          label: { show: false },
          data: categories.map((c, i) => ({ name: c, value: series[0]?.data[i] ?? 0 })),
        },
      ],
    };
  }

  const horizontal = kind === 'hbar';
  const valueAxis = {
    type: 'value' as const,
    splitNumber: 4,
    minInterval: integer ? 1 : undefined,
    axisLabel: label,
    splitLine: { lineStyle: { color: rule, type: 'dashed' as const } },
  };
  const categoryAxis = {
    type: 'category' as const,
    data: categories,
    boundaryGap: kind !== 'area',
    axisLine: { lineStyle: { color: rule } },
    axisTick: { show: false },
    axisLabel: { ...label, hideOverlap: true, width: horizontal ? 120 : undefined, overflow: 'truncate' as const },
  };

  return {
    ...base,
    grid: { left: 8, right: 16, top: 16, bottom: series.length > 1 ? 36 : 8, containLabel: true },
    tooltip: { ...tooltip, trigger: 'axis', axisPointer: { type: kind === 'area' ? 'line' : 'shadow', lineStyle: { color: rule } } },
    xAxis: horizontal ? valueAxis : categoryAxis,
    yAxis: horizontal ? { ...categoryAxis, inverse: true } : valueAxis,
    series: series.map((s, si) =>
      kind === 'area'
        ? {
            name: s.name,
            type: 'line',
            smooth: 0.3,
            connectNulls: false,
            symbolSize: 7,
            lineStyle: { width: 2.5 },
            emphasis: { focus: 'series' },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: `${palette[si % 5]}47` },
                { offset: 1, color: `${palette[si % 5]}00` },
              ]),
            },
            // Marked points (simulated data, BR-051) are diamonds, not circles, so colour is never the only cue.
            data: s.data.map((v, i) =>
              s.marked?.[i]
                ? { value: v, symbol: 'diamond', symbolSize: 12, itemStyle: { borderColor: card, borderWidth: 1.5 } }
                : { value: v, symbol: 'circle' },
            ),
          }
        : {
            name: s.name,
            type: 'bar',
            barMaxWidth: horizontal ? 20 : 28,
            itemStyle: { borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0] },
            emphasis: { focus: 'series' },
            label: horizontal ? { show: true, position: 'right', ...label } : undefined,
            data: s.data,
          },
    ),
  };
}

export default function EChart(props: ChartProps) {
  return (
    <ReactEChartsCore
      echarts={echarts}
      option={buildOption(props)}
      notMerge
      style={{ height: props.height ?? 240, width: '100%' }}
      opts={{ renderer: 'canvas' }}
    />
  );
}
