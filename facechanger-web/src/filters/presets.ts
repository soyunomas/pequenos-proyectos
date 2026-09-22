import type { FilterId } from '../types/face';
export const FILTERS: { id: FilterId; title: string; detail: string }[] = [
  { id: 'normal', title: 'Normal', detail: 'Sin efecto predefinido' },
  { id: 'nose-big', title: 'Nariz grande', detail: 'Ensanchamiento de nariz' },
  { id: 'nose-twisted', title: 'Nariz torcida', detail: 'Desplazamiento lateral' },
  { id: 'nose-long', title: 'Nariz larga', detail: 'Estiramiento de punta' },
  { id: 'eyes-big', title: 'Ojos grandes', detail: 'Amplía ambos ojos' },
  { id: 'eye-droop', title: 'Ojo caído', detail: 'Baja un ojo' },
  { id: 'mouth-twisted', title: 'Boca torcida', detail: 'Desplaza una comisura' },
  { id: 'face-long', title: 'Cara alargada', detail: 'Estira la cara verticalmente' },
];
