import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import { frameFromLandmarks } from './FaceWarpEngine';
import { mirrorLandmarks } from './CoordinateMapper';
import type { Face, Landmark } from '../types/face';
export class FaceTracker {
  private detector: FaceLandmarker | null = null;
  private smooth: Landmark[] | null = null;
  private lastFrameTime = -1;
  private lastDetection = -Infinity;
  private lastFace: Face | null = null;
  static async create(): Promise<FaceTracker> {
    const instance = new FaceTracker();
    const vision = await FilesetResolver.forVisionTasks(`${import.meta.env.BASE_URL}wasm`);
    instance.detector = await FaceLandmarker.createFromOptions(vision, {
      baseOptions: { modelAssetPath: `${import.meta.env.BASE_URL}models/face_landmarker.task`, delegate: 'CPU' },
      runningMode: 'VIDEO', numFaces: 1, minFaceDetectionConfidence: .5,
      minFacePresenceConfidence: .5, minTrackingConfidence: .5
    });
    return instance;
  }
  update(video: HTMLVideoElement, now: number): Face | null {
    if (!this.detector || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return this.lastFace;
    if (now - this.lastDetection < 55 || video.currentTime === this.lastFrameTime) return this.lastFace;
    this.lastDetection = now; this.lastFrameTime = video.currentTime;
    const raw = this.detector.detectForVideo(video, now).faceLandmarks[0];
    if (!raw) { this.smooth = null; this.lastFace = null; return null; }
    const mirrored = mirrorLandmarks(raw);
    if (!this.smooth || this.smooth.length !== mirrored.length) this.smooth = mirrored;
    else this.smooth = mirrored.map((p, i) => ({
      x: this.smooth![i].x * .56 + p.x * .44,
      y: this.smooth![i].y * .56 + p.y * .44,
      z: (this.smooth![i].z ?? 0) * .56 + (p.z ?? 0) * .44
    }));
    this.lastFace = { landmarks: this.smooth, frame: frameFromLandmarks(this.smooth) };
    return this.lastFace;
  }
  close(): void { this.detector?.close(); this.detector = null; this.lastFace = null; }
}
