#version 300 es
precision highp float;
in vec2 vUV;
out vec4 outColor;
uniform sampler2D uVideo;
uniform int uCount;
// Center in mirrored GL UV; radius normalized to width; scale is fractional.
uniform vec4 uControl[48];
uniform vec2 uDelta[48];
uniform float uAspect;
void main() {
  vec2 p = vUV;
  // Inverse mapping: every output pixel samples exactly one source pixel,
  // so there are no mesh holes or duplicated untouched facial features.
  for (int i = 47; i >= 0; --i) {
    if (i >= uCount) continue;
    vec4 ctl = uControl[i];
    vec2 delta = uDelta[i];
    vec2 destination = ctl.xy + delta;
    vec2 metric = (p - destination) * vec2(uAspect, 1.);
    float metricRadius = max(ctl.z * uAspect, 0.00001);
    float influence = exp(-2.7 * dot(metric, metric) / (metricRadius * metricRadius));
    p -= delta * influence;
    vec2 fromCenter = (p - ctl.xy) * vec2(uAspect, 1.);
    float swell = exp(-2.7 * dot(fromCenter, fromCenter) / (metricRadius * metricRadius));
    p = ctl.xy + (p - ctl.xy) / max(.55, 1. + ctl.w * swell);
  }
  // Mirror source image AFTER the warp (all touch anchors use mirrored coordinates).
  outColor = texture(uVideo, vec2(1. - p.x, p.y));
}
