precision mediump float;

varying vec2 v_TextureCoords;
varying vec4 v_Color;

uniform sampler2D u_Texture;

void main() {
    gl_FragColor = v_Color * texture2D(u_Texture, v_TextureCoords);
}
