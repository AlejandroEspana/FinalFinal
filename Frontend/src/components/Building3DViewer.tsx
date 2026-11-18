import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  Compass,
  Eye,
  Layers,
  Maximize2,
  RotateCcw,
  Sparkles,
  Zap,
  AlertTriangle
} from 'lucide-react';
import { SensorPlantStatusDto } from '../types/contracts';

interface Building3DViewerProps {
  sensors: SensorPlantStatusDto[];
  onInjectSpike: (sensorId: string, vibrationValue: number, burstCount: number) => void;
  onResetMachine: (sensorId: string) => void;
}

interface Sensor3DMeta {
  sensorId: string;
  floor: 1 | 2 | 3;
  floorName: string;
  machineName: string;
  rx: number;
  rz: number;
}

const SENSOR_3D_LAYOUT: Sensor3DMeta[] = [
  { sensorId: 'sensor_A', floor: 1, floorName: 'Nivel 1 — Sótano de Potencia', machineName: 'Turbina Principal A1', rx: -0.55, rz: -0.45 },
  { sensorId: 'sensor_B', floor: 1, floorName: 'Nivel 1 — Sótano de Potencia', machineName: 'Generador Síncrono B2', rx: 0.55, rz: -0.45 },
  { sensorId: 'sensor_C', floor: 1, floorName: 'Nivel 1 — Sótano de Potencia', machineName: 'Bomba Hidráulica C3', rx: -0.55, rz: 0.45 },
  { sensorId: 'sensor_D', floor: 1, floorName: 'Nivel 1 — Sótano de Potencia', machineName: 'Reductor Mecánico D4', rx: 0.55, rz: 0.45 },

  { sensorId: 'sensor_E', floor: 2, floorName: 'Nivel 2 — Planta de Procesos', machineName: 'Compresor Centrífugo E1', rx: -0.58, rz: -0.35 },
  { sensorId: 'sensor_F', floor: 2, floorName: 'Nivel 2 — Planta de Procesos', machineName: 'Extrusora Industrial F2', rx: 0.0, rz: 0.45 },
  { sensorId: 'sensor_G', floor: 2, floorName: 'Nivel 2 — Planta de Procesos', machineName: 'Ventilador Tiro Inducido G3', rx: 0.58, rz: -0.35 },

  { sensorId: 'sensor_H', floor: 3, floorName: 'Nivel 3 — Cubierta Técnica & HVAC', machineName: 'Chiller Enfriamiento H1', rx: -0.55, rz: 0.0 },
  { sensorId: 'sensor_I', floor: 3, floorName: 'Nivel 3 — Cubierta Técnica & HVAC', machineName: 'Torre Refrigeración I2', rx: 0.0, rz: -0.48 },
  { sensorId: 'sensor_J', floor: 3, floorName: 'Nivel 3 — Cubierta Técnica & HVAC', machineName: 'Subestación Eléctrica J3', rx: 0.55, rz: 0.0 }
];

interface ProjectedNodeHit {
  sensorId: string;
  screenX: number;
  screenY: number;
  radius: number;
}

export const Building3DViewer: React.FC<Building3DViewerProps> = ({
  sensors,
  onInjectSpike,
  onResetMachine
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const hitNodesRef = useRef<ProjectedNodeHit[]>([]);

  const [yaw, setYaw] = useState<number>(0.68);
  const [pitch, setPitch] = useState<number>(0.48);
  const [zoom, setZoom] = useState<number>(1.05);
  const [autoRotate, setAutoRotate] = useState<boolean>(true);
  const [explodedView, setExplodedView] = useState<boolean>(false);
  const [selectedFloor, setSelectedFloor] = useState<number | 'ALL'>('ALL');
  const [selectedSensorId, setSelectedSensorId] = useState<string>('sensor_A');

  const isDraggingRef = useRef<boolean>(false);
  const lastMouseRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });

  const sensorMap = useMemo(() => {
    const map = new Map<string, SensorPlantStatusDto>();
    sensors.forEach((s) => map.set(s.sensorId, s));
    return map;
  }, [sensors]);

  useEffect(() => {
    const criticalSensor = sensors.find(
      (s) => s.severity === 'CRITICAL' || s.operationalStatus === 'EMERGENCY_HALTED'
    );
    if (criticalSensor) {
      setSelectedSensorId(criticalSensor.sensorId);
    }
  }, [sensors]);

  const selectedSensor = sensorMap.get(selectedSensorId) ?? sensors[0];
  const selectedMeta =
    SENSOR_3D_LAYOUT.find((m) => m.sensorId === selectedSensorId) ?? SENSOR_3D_LAYOUT[0];

  useEffect(() => {
    let animationFrameId: number;
    let phase = 0;

    const renderScene = () => {
      phase += 0.04;
      if (autoRotate && !isDraggingRef.current) {
        setYaw((prev) => prev + 0.0045);
      }

      const canvas = canvasRef.current;
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.getBoundingClientRect();
      if (canvas.width !== Math.floor(rect.width * dpr) || canvas.height !== Math.floor(rect.height * dpr)) {
        canvas.width = Math.floor(rect.width * dpr);
        canvas.height = Math.floor(rect.height * dpr);
      }

      ctx.save();
      ctx.scale(dpr, dpr);

      const width = rect.width;
      const height = rect.height;
      const cx = width * 0.5;
      const cy = height * 0.56;

      const bgGrad = ctx.createRadialGradient(cx, cy * 0.8, 30, cx, cy, Math.max(width, height) * 0.75);
      bgGrad.addColorStop(0, '#FFFDF9');
      bgGrad.addColorStop(0.65, '#F6EFE2');
      bgGrad.addColorStop(1, '#EDE3D2');
      ctx.fillStyle = bgGrad;
      ctx.fillRect(0, 0, width, height);

      const scale = Math.min(width, height) * 0.27 * zoom;
      const cosY = Math.cos(yaw);
      const sinY = Math.sin(yaw);
      const cosP = Math.cos(pitch);
      const sinP = Math.sin(pitch);

      const project = (x: number, y: number, z: number) => {
        const rx = x * cosY - z * sinY;
        const rz = x * sinY + z * cosY;
        const ry = y * cosP - rz * sinP;
        const depth = y * sinP + rz * cosP;
        const perspective = 1 / (1 + depth * 0.14);
        return {
          x: cx + rx * scale * perspective,
          y: cy - ry * scale * perspective,
          depth,
          scale: perspective
        };
      };

      ctx.strokeStyle = 'rgba(166, 148, 120, 0.22)';
      ctx.lineWidth = 1;
      for (let g = -1.6; g <= 1.61; g += 0.4) {
        const p1 = project(g, -0.85, -1.6);
        const p2 = project(g, -0.85, 1.6);
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();

        const p3 = project(-1.6, -0.85, g);
        const p4 = project(1.6, -0.85, g);
        ctx.beginPath();
        ctx.moveTo(p3.x, p3.y);
        ctx.lineTo(p4.x, p4.y);
        ctx.stroke();
      }

      const floorGap = explodedView ? 0.72 : 0.48;
      const baseFloorY = -0.68;
      const bw = 1.15;
      const bd = 0.88;
      const wallH = 0.34;

      const newHits: ProjectedNodeHit[] = [];

      ([1, 2, 3] as const).forEach((floorNum) => {
        if (selectedFloor !== 'ALL' && selectedFloor !== floorNum) {
          return;
        }

        const y0 = baseFloorY + (floorNum - 1) * floorGap;
        const y1 = y0 + wallH;

        const floorSensors = SENSOR_3D_LAYOUT.filter((m) => m.floor === floorNum);
        const hasCriticalOnFloor = floorSensors.some((m) => {
          const s = sensorMap.get(m.sensorId);
          return s?.severity === 'CRITICAL' || s?.operationalStatus === 'EMERGENCY_HALTED';
        });
        const hasWarningOnFloor = floorSensors.some(
          (m) => sensorMap.get(m.sensorId)?.severity === 'WARNING'
        );

        const c0 = project(-bw, y0, -bd);
        const c1 = project(bw, y0, -bd);
        const c2 = project(bw, y0, bd);
        const c3 = project(-bw, y0, bd);

        ctx.beginPath();
        ctx.moveTo(c0.x, c0.y);
        ctx.lineTo(c1.x, c1.y);
        ctx.lineTo(c2.x, c2.y);
        ctx.lineTo(c3.x, c3.y);
        ctx.closePath();

        if (hasCriticalOnFloor) {
          ctx.fillStyle = `rgba(220, 38, 38, ${0.18 + 0.08 * Math.sin(phase * 2.5)})`;
          ctx.strokeStyle = 'rgba(185, 28, 28, 0.75)';
        } else if (hasWarningOnFloor) {
          ctx.fillStyle = 'rgba(217, 119, 6, 0.16)';
          ctx.strokeStyle = 'rgba(180, 83, 9, 0.65)';
        } else {
          ctx.fillStyle = 'rgba(235, 224, 204, 0.72)';
          ctx.strokeStyle = 'rgba(140, 122, 95, 0.55)';
        }
        ctx.lineWidth = 1.8;
        ctx.fill();
        ctx.stroke();

        const midX1 = project(0, y0, -bd);
        const midX2 = project(0, y0, bd);
        const midZ1 = project(-bw, y0, 0);
        const midZ2 = project(bw, y0, 0);
        ctx.strokeStyle = 'rgba(140, 122, 95, 0.25)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(midX1.x, midX1.y);
        ctx.lineTo(midX2.x, midX2.y);
        ctx.moveTo(midZ1.x, midZ1.y);
        ctx.lineTo(midZ2.x, midZ2.y);
        ctx.stroke();

        const t0 = project(-bw, y1, -bd);
        const t1 = project(bw, y1, -bd);
        const t2 = project(bw, y1, bd);
        const t3 = project(-bw, y1, bd);

        ctx.strokeStyle = 'rgba(120, 102, 78, 0.45)';
        ctx.lineWidth = 2;
        [
          [c0, t0],
          [c1, t1],
          [c2, t2],
          [c3, t3]
        ].forEach(([bPt, tPt]) => {
          ctx.beginPath();
          ctx.moveTo(bPt.x, bPt.y);
          ctx.lineTo(tPt.x, tPt.y);
          ctx.stroke();
        });

        const labelAnchor = project(-bw - 0.18, y0 + 0.1, bd);
        ctx.fillStyle = '#4A3F35';
        ctx.font = 'bold 11px Inter, system-ui, sans-serif';
        ctx.textAlign = 'right';
        ctx.fillText(`NIVEL ${floorNum}`, labelAnchor.x, labelAnchor.y);

        const sortedSensors = [...floorSensors].sort((a, b) => {
          const pa = project(a.rx * bw, y0, a.rz * bd);
          const pb = project(b.rx * bw, y0, b.rz * bd);
          return pb.depth - pa.depth;
        });

        sortedSensors.forEach((meta) => {
          const dto = sensorMap.get(meta.sensorId);
          const severity = dto?.severity ?? 'NORMAL';
          const opStatus = dto?.operationalStatus ?? 'ACTIVE';
          const vib = dto?.lastVibration ?? 28.0;
          const isHalted = opStatus === 'EMERGENCY_HALTED';
          const isCritical = severity === 'CRITICAL' || isHalted;
          const isWarning = severity === 'WARNING' && !isHalted;
          const isSelected = meta.sensorId === selectedSensorId;

          const sx = meta.rx * bw;
          const sz = meta.rz * bd;
          const basePos = project(sx, y0 + 0.04, sz);
          const topPos = project(sx, y0 + 0.19, sz);

          if (isCritical || isWarning) {
            const pulseRadius = (16 + ((phase * 14) % 24)) * basePos.scale;
            ctx.beginPath();
            ctx.ellipse(
              basePos.x,
              basePos.y,
              pulseRadius * 1.25,
              pulseRadius * 0.65,
              0,
              0,
              Math.PI * 2
            );
            ctx.strokeStyle = isCritical
              ? 'rgba(220, 38, 38, 0.65)'
              : 'rgba(217, 119, 6, 0.6)';
            ctx.lineWidth = 2;
            ctx.stroke();

            const beamTop = project(sx, y0 + 0.36, sz);
            const beamGrad = ctx.createLinearGradient(basePos.x, basePos.y, beamTop.x, beamTop.y);
            beamGrad.addColorStop(
              0,
              isCritical ? 'rgba(220, 38, 38, 0.45)' : 'rgba(217, 119, 6, 0.38)'
            );
            beamGrad.addColorStop(1, 'rgba(220, 38, 38, 0)');
            ctx.strokeStyle = beamGrad;
            ctx.lineWidth = 10 * basePos.scale;
            ctx.beginPath();
            ctx.moveTo(basePos.x, basePos.y);
            ctx.lineTo(beamTop.x, beamTop.y);
            ctx.stroke();
          }

          const nodeRadius = (isSelected ? 12.5 : 10) * topPos.scale;
          ctx.beginPath();
          ctx.moveTo(basePos.x - nodeRadius, basePos.y);
          ctx.lineTo(topPos.x - nodeRadius, topPos.y);
          ctx.lineTo(topPos.x + nodeRadius, topPos.y);
          ctx.lineTo(basePos.x + nodeRadius, basePos.y);
          ctx.closePath();
          ctx.fillStyle = isHalted
            ? '#881337'
            : isCritical
            ? '#DC2626'
            : isWarning
            ? '#D97706'
            : '#059669';
          ctx.fill();

          ctx.beginPath();
          ctx.arc(topPos.x, topPos.y, nodeRadius, 0, Math.PI * 2);
          ctx.fillStyle = isHalted
            ? '#BE123C'
            : isCritical
            ? '#EF4444'
            : isWarning
            ? '#F59E0B'
            : '#10B981';
          ctx.fill();
          ctx.lineWidth = isSelected ? 2.8 : 1.5;
          ctx.strokeStyle = isSelected ? '#1E293B' : '#FFFDF9';
          ctx.stroke();

          const labelY = topPos.y - nodeRadius - 7;
          const tagText = `${meta.sensorId.replace('sensor_', 'S-')} • ${vib.toFixed(0)}mm/s`;
          ctx.font = `${isSelected || isCritical ? 'bold ' : ''}10px ui-monospace, monospace`;
          const textW = ctx.measureText(tagText).width + 8;

          ctx.fillStyle = isCritical
            ? 'rgba(153, 27, 27, 0.92)'
            : isWarning
            ? 'rgba(146, 64, 14, 0.92)'
            : isSelected
            ? 'rgba(44, 37, 35, 0.92)'
            : 'rgba(255, 253, 249, 0.88)';
          ctx.strokeStyle = isCritical
            ? '#FCA5A5'
            : isWarning
            ? '#FCD34D'
            : 'rgba(180, 162, 135, 0.7)';
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.roundRect(topPos.x - textW / 2, labelY - 11, textW, 15, 4);
          ctx.fill();
          ctx.stroke();

          ctx.fillStyle = isCritical || isWarning || isSelected ? '#FFFDF9' : '#2C2523';
          ctx.textAlign = 'center';
          ctx.fillText(tagText, topPos.x, labelY);

          newHits.push({
            sensorId: meta.sensorId,
            screenX: topPos.x,
            screenY: topPos.y,
            radius: Math.max(16, nodeRadius * 1.5)
          });
        });

        if (floorNum === 3) {
          ctx.beginPath();
          ctx.moveTo(t0.x, t0.y);
          ctx.lineTo(t1.x, t1.y);
          ctx.lineTo(t2.x, t2.y);
          ctx.lineTo(t3.x, t3.y);
          ctx.closePath();
          ctx.fillStyle = 'rgba(255, 253, 249, 0.18)';
          ctx.strokeStyle = 'rgba(140, 122, 95, 0.45)';
          ctx.lineWidth = 1.4;
          ctx.fill();
          ctx.stroke();
        }
      });

      hitNodesRef.current = newHits;
      ctx.restore();

      animationFrameId = window.requestAnimationFrame(renderScene);
    };

    animationFrameId = window.requestAnimationFrame(renderScene);
    return () => window.cancelAnimationFrame(animationFrameId);
  }, [yaw, pitch, zoom, autoRotate, explodedView, selectedFloor, selectedSensorId, sensorMap]);

  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    isDraggingRef.current = true;
    lastMouseRef.current = { x: e.clientX, y: e.clientY };

    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const clickY = e.clientY - rect.top;

    for (let i = hitNodesRef.current.length - 1; i >= 0; i--) {
      const node = hitNodesRef.current[i];
      const dist = Math.hypot(clickX - node.screenX, clickY - node.screenY);
      if (dist <= node.radius) {
        setSelectedSensorId(node.sensorId);
        break;
      }
    }
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDraggingRef.current) return;
    const dx = e.clientX - lastMouseRef.current.x;
    const dy = e.clientY - lastMouseRef.current.y;
    lastMouseRef.current = { x: e.clientX, y: e.clientY };

    setYaw((prev) => prev + dx * 0.008);
    setPitch((prev) => Math.max(0.18, Math.min(1.15, prev + dy * 0.006)));
  };

  const handleMouseUp = () => {
    isDraggingRef.current = false;
  };

  const isSelectedHalted = selectedSensor?.operationalStatus === 'EMERGENCY_HALTED';

  return (
    <section className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-2xl p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 pb-4 border-b border-[#EDE4D3]">
        <div className="flex items-center gap-2.5">
          <div className="h-9 w-9 rounded-xl bg-[#F4ECE0] border border-[#DFD2BC] flex items-center justify-center text-[#8C6239]">
            <Box className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-[#2C2523]">
                Gemelo Digital 3D del Edificio Industrial (Simulación Espacial de Sensores)
              </h2>
              <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-[#F4ECE0] text-[#7A542E] border border-[#DFD2BC]">
                3 Niveles • 10 Sensores Interactivos
              </span>
            </div>
            <p className="text-xs text-[#6E6256]">
              Arrastra con el mouse para rotar el edificio en 360° o haz clic sobre cualquier sensor 3D para inspeccionarlo o inyectar fallas.
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-xs">
          <div className="flex items-center bg-[#F6EFE2] border border-[#E2D6C1] rounded-lg p-0.5">
            {(['ALL', 1, 2, 3] as const).map((fl) => (
              <button
                key={String(fl)}
                onClick={() => setSelectedFloor(fl)}
                className={`px-2.5 py-1 rounded-md font-semibold transition ${
                  selectedFloor === fl
                    ? 'bg-[#8C6239] text-white shadow-sm'
                    : 'text-[#5C5146] hover:text-[#2C2523]'
                }`}
              >
                {fl === 'ALL' ? 'Todos' : `Nivel ${fl}`}
              </button>
            ))}
          </div>

          <button
            onClick={() => setExplodedView((v) => !v)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border font-semibold transition ${
              explodedView
                ? 'bg-[#8C6239] text-white border-[#7A542E]'
                : 'bg-[#FAF5EC] text-[#4A3F35] border-[#DFD2BC] hover:bg-[#F2E9DA]'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            {explodedView ? 'Vista Compacta' : 'Expandir Pisos 3D'}
          </button>

          <button
            onClick={() => setAutoRotate((v) => !v)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border font-semibold transition ${
              autoRotate
                ? 'bg-[#EDE3D2] text-[#2C2523] border-[#D5C6AE]'
                : 'bg-[#FAF5EC] text-[#6E6256] border-[#DFD2BC]'
            }`}
          >
            <Compass className="w-3.5 h-3.5" />
            {autoRotate ? 'Rotación: ON' : 'Rotación: OFF'}
          </button>

          <button
            onClick={() => {
              setYaw(0.68);
              setPitch(0.48);
              setZoom(1.05);
            }}
            className="px-2.5 py-1.5 rounded-lg bg-[#FAF5EC] hover:bg-[#F2E9DA] text-[#4A3F35] border border-[#DFD2BC]"
            title="Restablecer cámara 3D"
          >
            <Maximize2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 mt-4">
        <div className="lg:col-span-8 relative rounded-xl overflow-hidden border border-[#E2D6C1] bg-[#F6EFE2] h-[390px]">
          <canvas
            ref={canvasRef}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            className="w-full h-full cursor-grab active:cursor-grabbing block"
          />

          <div className="absolute bottom-3 left-3 flex flex-wrap items-center gap-3 bg-[#FFFDF9]/90 backdrop-blur-sm border border-[#DFD2BC] rounded-lg px-3 py-1.5 text-[11px] text-[#4A3F35] shadow-sm">
            <span className="flex items-center gap-1.5 font-medium">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-600 inline-block" />
              Normal (&le;75 mm/s)
            </span>
            <span className="flex items-center gap-1.5 font-medium">
              <span className="w-2.5 h-2.5 rounded-full bg-amber-600 inline-block" />
              Advertencia (&gt;75 x3)
            </span>
            <span className="flex items-center gap-1.5 font-medium">
              <span className="w-2.5 h-2.5 rounded-full bg-red-600 animate-ping inline-block" />
              Crítico (&gt;90 mm/s) / Detenido
            </span>
          </div>

          <div className="absolute top-3 right-3 flex flex-col gap-1">
            <button
              onClick={() => setZoom((z) => Math.min(1.5, z + 0.12))}
              className="w-7 h-7 rounded-lg bg-[#FFFDF9]/90 hover:bg-white border border-[#DFD2BC] text-[#2C2523] font-bold text-sm shadow-sm"
            >
              +
            </button>
            <button
              onClick={() => setZoom((z) => Math.max(0.7, z - 0.12))}
              className="w-7 h-7 rounded-lg bg-[#FFFDF9]/90 hover:bg-white border border-[#DFD2BC] text-[#2C2523] font-bold text-sm shadow-sm"
            >
              &minus;
            </button>
          </div>
        </div>

        <div className="lg:col-span-4 bg-[#FAF6EE] border border-[#E5DCCB] rounded-xl p-4 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between border-b border-[#E5DCCB] pb-3">
              <div>
                <span className="text-[10px] font-mono uppercase tracking-wider text-[#8C6239] font-bold">
                  INSPECTOR DE GEMELO DIGITAL 3D
                </span>
                <h3 className="text-base font-bold text-[#2C2523] mt-0.5 flex items-center gap-2">
                  <span>{selectedMeta.sensorId}</span>
                  <span className="text-xs font-normal text-[#6E6256]">— {selectedMeta.machineName}</span>
                </h3>
              </div>
              <Eye className="w-4 h-4 text-[#8C6239]" />
            </div>

            <div className="mt-3 space-y-2.5 text-xs">
              <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-lg p-2.5">
                <span className="text-[#7A6E60] block text-[11px]">Ubicación en Edificio:</span>
                <span className="font-semibold text-[#2C2523]">{selectedMeta.floorName}</span>
              </div>

              <div className="grid grid-cols-2 gap-2.5">
                <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-lg p-2.5">
                  <span className="text-[#7A6E60] block text-[11px]">Vibración Actual:</span>
                  <span className="text-lg font-mono font-extrabold text-[#2C2523]">
                    {(selectedSensor?.lastVibration ?? 0).toFixed(2)}{' '}
                    <span className="text-[11px] font-normal text-[#6E6256]">mm/s</span>
                  </span>
                </div>

                <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-lg p-2.5">
                  <span className="text-[#7A6E60] block text-[11px]">Estado Operativo:</span>
                  <span
                    className={`inline-block mt-1 text-[11px] font-mono font-bold px-2 py-0.5 rounded ${
                      selectedSensor?.severity === 'CRITICAL' || isSelectedHalted
                        ? 'bg-red-100 text-red-800 border border-red-300'
                        : selectedSensor?.severity === 'WARNING'
                        ? 'bg-amber-100 text-amber-800 border border-amber-300'
                        : 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                    }`}
                  >
                    {isSelectedHalted ? 'EMERGENCY_HALTED' : selectedSensor?.severity ?? 'NORMAL'}
                  </span>
                </div>
              </div>

              <div className="bg-[#FFFDF9] border border-[#E5DCCB] rounded-lg p-2.5">
                <span className="text-[#7A6E60] block text-[11px]">Diagnóstico del Sensor:</span>
                <p className="text-[#3D342E] mt-0.5 leading-relaxed">
                  {selectedSensor?.statusDescription}
                </p>
              </div>
            </div>

            <div className="mt-3">
              <span className="text-[11px] font-semibold text-[#6E6256] block mb-1.5">
                Seleccionar Sensor en el Edificio:
              </span>
              <div className="grid grid-cols-5 gap-1.5">
                {SENSOR_3D_LAYOUT.map((item) => {
                  const st = sensorMap.get(item.sensorId);
                  const crit = st?.severity === 'CRITICAL' || st?.operationalStatus === 'EMERGENCY_HALTED';
                  const warn = st?.severity === 'WARNING' && !crit;
                  return (
                    <button
                      key={item.sensorId}
                      onClick={() => setSelectedSensorId(item.sensorId)}
                      className={`py-1 px-1.5 rounded text-[11px] font-mono font-bold border transition ${
                        selectedSensorId === item.sensorId
                          ? 'ring-2 ring-[#8C6239] '
                          : ''
                      }${
                        crit
                          ? 'bg-red-600 text-white border-red-700'
                          : warn
                          ? 'bg-amber-500 text-white border-amber-600'
                          : 'bg-[#FFFDF9] text-[#2C2523] border-[#D8CBB5] hover:bg-[#F3EBDD]'
                      }`}
                    >
                      {item.sensorId.replace('sensor_', 'S-')}
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          <div className="mt-4 pt-3 border-t border-[#E5DCCB] flex flex-col gap-2">
            <div className="flex gap-2">
              <button
                onClick={() => onInjectSpike(selectedMeta.sensorId, 96.8, 1)}
                disabled={isSelectedHalted}
                className="flex-1 py-2 px-2.5 rounded-lg bg-red-700 hover:bg-red-600 text-white text-xs font-semibold flex items-center justify-center gap-1.5 shadow-sm disabled:opacity-40 transition"
              >
                <Zap className="w-3.5 h-3.5" />
                Simular Crítico (&gt;90)
              </button>

              <button
                onClick={() => onInjectSpike(selectedMeta.sensorId, 82.4, 3)}
                disabled={isSelectedHalted}
                className="flex-1 py-2 px-2.5 rounded-lg bg-amber-600 hover:bg-amber-500 text-white text-xs font-semibold flex items-center justify-center gap-1.5 shadow-sm disabled:opacity-40 transition"
              >
                <AlertTriangle className="w-3.5 h-3.5" />
                Ráfaga 3x &gt;75
              </button>
            </div>

            {(isSelectedHalted || (selectedSensor && selectedSensor.severity !== 'NORMAL')) && (
              <button
                onClick={() => onResetMachine(selectedMeta.sensorId)}
                className="w-full py-2 px-3 rounded-lg bg-emerald-700 hover:bg-emerald-600 text-white text-xs font-semibold flex items-center justify-center gap-1.5 shadow-sm transition"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Reactivar Máquina y Normalizar Piso {selectedMeta.floor}
              </button>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};
