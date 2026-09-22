import { MAX_CONTROLS } from './geometry.js';
const VERTEX = `#version 300 es
in vec2 aPosition;
out vec2 vUV;
void main(){vUV = aPosition * .5 + .5; gl_Position = vec4(aPosition, 0., 1.);}`;
const FRAGMENT = `#version 300 es
precision highp float;
in vec2 vUV;
out vec4 outColor;
uniform sampler2D uVideo;
uniform int uCount;
uniform vec4 uControl[${MAX_CONTROLS}];
uniform vec2 uDelta[${MAX_CONTROLS}];
uniform float uAspect;
void main(){
  // p: espejo top-left; textura original top-left se convierte a WebGL bottom-left.
  vec2 p = vec2(vUV.x, 1. - vUV.y);
  for (int i = 0; i < ${MAX_CONTROLS}; i++) {
    int k = uCount - 1 - i;
    if (k < 0) break;
    vec4 ctl = uControl[k]; vec2 delta = uDelta[k];
    vec2 dest = ctl.xy + delta;
    vec2 metric = (p - dest) * vec2(uAspect, 1.);
    float r2 = max(.00000001, ctl.z * ctl.z * uAspect * uAspect);
    float influence = exp(-2.7 * dot(metric, metric) / r2);
    p -= delta * influence;
    vec2 fromCenter = (p - ctl.xy) * vec2(uAspect, 1.);
    float swell = exp(-2.7 * dot(fromCenter, fromCenter) / r2);
    p = ctl.xy + (p - ctl.xy) / max(.55, 1. + ctl.w * swell);
  }
  // UNPACK_FLIP_Y_WEBGL=true: UV y=1 corresponde al borde superior de la cámara.
  outColor = texture(uVideo, vec2(1. - p.x, 1. - p.y));
}`;
function compile(gl, kind, source) {
  const shader = gl.createShader(kind);
  if (!shader) throw Error('No se puede crear shader WebGL2');
  gl.shaderSource(shader, source); gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const reason = gl.getShaderInfoLog(shader); gl.deleteShader(shader); throw Error(reason || 'Error GLSL');
  }
  return shader;
}
export class Renderer {
  constructor(canvas) {
    const gl = canvas.getContext('webgl2', { alpha: false, antialias: false });
    if (!gl) throw Error('Este navegador o GPU no tiene WebGL2 disponible');
    this.canvas = canvas; this.gl = gl;
    const vs = compile(gl, gl.VERTEX_SHADER, VERTEX), fs = compile(gl, gl.FRAGMENT_SHADER, FRAGMENT);
    const program = gl.createProgram(); if (!program) throw Error('No se puede crear programa WebGL2');
    gl.attachShader(program, vs); gl.attachShader(program, fs); gl.linkProgram(program);
    gl.deleteShader(vs); gl.deleteShader(fs);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(program) || 'Error WebGL2');
    this.program = program;
    gl.useProgram(program);
    this.buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const location = gl.getAttribLocation(program, 'aPosition');
    gl.enableVertexAttribArray(location); gl.vertexAttribPointer(location, 2, gl.FLOAT, false, 0, 0);
    this.texture = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, this.texture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.uniform1i(gl.getUniformLocation(program, 'uVideo'), 0);
    this.uCount = gl.getUniformLocation(program, 'uCount');
    this.uControl = gl.getUniformLocation(program, 'uControl[0]');
    this.uDelta = gl.getUniformLocation(program, 'uDelta[0]');
    this.uAspect = gl.getUniformLocation(program, 'uAspect');
    this.bufferControls = new Float32Array(MAX_CONTROLS * 4);
    this.bufferDeltas = new Float32Array(MAX_CONTROLS * 2);
  }
  draw(video, controls) {
    if (!video.videoWidth || video.readyState < 2) return;
    const { gl, canvas } = this;
    if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
      canvas.width = video.videoWidth; canvas.height = video.videoHeight;
      gl.viewport(0, 0, canvas.width, canvas.height);
    }
    this.bufferControls.fill(0); this.bufferDeltas.fill(0);
    for (let i = 0; i < Math.min(controls.length, MAX_CONTROLS); i++) {
      const c = controls[i];
      this.bufferControls.set([c.x, c.y, c.radius, c.scale], i * 4);
      this.bufferDeltas.set([c.dx, c.dy], i * 2);
    }
    gl.useProgram(this.program);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, this.texture);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);
    gl.uniform1i(this.uCount, Math.min(controls.length, MAX_CONTROLS));
    gl.uniform4fv(this.uControl, this.bufferControls);
    gl.uniform2fv(this.uDelta, this.bufferDeltas);
    gl.uniform1f(this.uAspect, video.videoWidth / video.videoHeight);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }
  dispose() {
    const g = this.gl; g.deleteTexture(this.texture); g.deleteBuffer(this.buffer); g.deleteProgram(this.program);
  }
}
