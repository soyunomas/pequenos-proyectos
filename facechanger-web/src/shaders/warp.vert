#version 300 es
in vec2 aPosition;
out vec2 vUV;
void main() {
  vUV = aPosition * .5 + .5;
  gl_Position = vec4(aPosition, 0., 1.);
}
