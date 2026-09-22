import vertexSource from '../shaders/warp.vert?raw';
import fragmentSource from '../shaders/warp.frag?raw';
import { MAX_CONTROLS } from './FaceWarpEngine';
import type { WarpControl } from '../types/face';

function shader(gl: WebGL2RenderingContext, type: number, source: string): WebGLShader {
  const result = gl.createShader(type);
  if (!result) throw new Error('No se pudo crear el shader');
  gl.shaderSource(result, source); gl.compileShader(result);
  if (!gl.getShaderParameter(result, gl.COMPILE_STATUS)) throw new Error(String(gl.getShaderInfoLog(result)));
  return result;
}
export class WebGLRenderer {
  private gl: WebGL2RenderingContext;
  private program: WebGLProgram;
  private buffer: WebGLBuffer;
  private texture: WebGLTexture;
  private count: WebGLUniformLocation | null;
  private controls: WebGLUniformLocation | null;
  private deltas: WebGLUniformLocation | null;
  private aspect: WebGLUniformLocation | null;
  constructor(private readonly canvas: HTMLCanvasElement) {
    const gl = canvas.getContext('webgl2', { alpha: false, antialias: false, preserveDrawingBuffer: false });
    if (!gl) throw new Error('WebGL2 no está disponible. Comprueba que la aceleración gráfica está activada.');
    this.gl = gl;
    const vs = shader(gl, gl.VERTEX_SHADER, vertexSource), fs = shader(gl, gl.FRAGMENT_SHADER, fragmentSource);
    const program = gl.createProgram(); if (!program) throw new Error('No se pudo crear el programa WebGL');
    gl.attachShader(program, vs); gl.attachShader(program, fs); gl.linkProgram(program);
    gl.deleteShader(vs); gl.deleteShader(fs);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(String(gl.getProgramInfoLog(program)));
    this.program = program;
    const buffer = gl.createBuffer(), texture = gl.createTexture();
    if (!buffer || !texture) throw new Error('No se pudieron reservar recursos WebGL');
    this.buffer = buffer; this.texture = texture;
    gl.useProgram(program);
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const aPosition = gl.getAttribLocation(program, 'aPosition');
    gl.enableVertexAttribArray(aPosition); gl.vertexAttribPointer(aPosition, 2, gl.FLOAT, false, 0, 0);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, texture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.uniform1i(gl.getUniformLocation(program, 'uVideo'), 0);
    this.count = gl.getUniformLocation(program, 'uCount');
    this.controls = gl.getUniformLocation(program, 'uControl[0]');
    this.deltas = gl.getUniformLocation(program, 'uDelta[0]');
    this.aspect = gl.getUniformLocation(program, 'uAspect');
  }
  draw(video: HTMLVideoElement, controls: WarpControl[]): void {
    if (!video.videoWidth || !video.videoHeight || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return;
    const gl = this.gl;
    if (this.canvas.width !== video.videoWidth || this.canvas.height !== video.videoHeight) {
      this.canvas.width = video.videoWidth; this.canvas.height = video.videoHeight;
      gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    }
    const c = new Float32Array(MAX_CONTROLS * 4), d = new Float32Array(MAX_CONTROLS * 2);
    controls.slice(0, MAX_CONTROLS).forEach((ctl, i) => {
      c.set([ctl.center.x, 1 - ctl.center.y, ctl.radius, ctl.scale], i * 4);
      d.set([ctl.delta.x, -ctl.delta.y], i * 2);
    });
    gl.useProgram(this.program);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, this.texture);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);
    gl.uniform1i(this.count, Math.min(controls.length, MAX_CONTROLS));
    gl.uniform4fv(this.controls, c); gl.uniform2fv(this.deltas, d);
    gl.uniform1f(this.aspect, video.videoWidth / video.videoHeight);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }
  dispose(): void {
    const gl = this.gl;
    gl.deleteTexture(this.texture); gl.deleteBuffer(this.buffer); gl.deleteProgram(this.program);
  }
}
