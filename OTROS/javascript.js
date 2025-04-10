document.addEventListener('DOMContentLoaded', () => {
    const toggleButton = document.getElementById('toggleButton');
    const statusElement = document.getElementById('status');
    const asciiOutput = document.getElementById('asciiOutput');

    let audioContext = null;
    let analyser = null;
    let microphoneSource = null;
    let dataArray = null;
    let animationFrameId = null;
    let isInitialized = false;
    let isRunning = false;

    // --- Configuración ---
    const FFT_SIZE = 512;
    const SMOOTHING = 0.8;
    const ASCII_CHARS = ' .,:;irsXA253hMHGS#9B&@';
    const OUTPUT_WIDTH = 100; 
    const OUTPUT_HEIGHT = 50;

    // --- Funciones ---

    async function initAudio() { /* ... (Sin cambios) ... */
        if (isInitialized) return;
        statusElement.textContent = 'Inicializando AudioContext...';
        try {
            audioContext = new (window.AudioContext || window.webkitAudioContext)();
             await audioContext.resume();
            statusElement.textContent = 'Solicitando acceso al micrófono...';
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
            statusElement.textContent = 'Configurando Analizador...';
            microphoneSource = audioContext.createMediaStreamSource(stream);
            analyser = audioContext.createAnalyser();
            analyser.fftSize = FFT_SIZE;
            analyser.smoothingTimeConstant = SMOOTHING;
            microphoneSource.connect(analyser);
            const bufferLength = analyser.frequencyBinCount;
            dataArray = new Uint8Array(bufferLength);
            isInitialized = true;
            statusElement.textContent = 'Listo para iniciar.';
            toggleButton.disabled = false;
            console.log('Audio inicializado correctamente.');
        } catch (err) {
            console.error('Error al inicializar el audio:', err);
            statusElement.textContent = `Error: ${err.message}. Asegúrate de permitir el acceso al micrófono.`;
            isInitialized = false;
            toggleButton.disabled = true;
        }
     }

    function mapValueToAscii(value) { /* ... (Sin cambios) ... */
        const index = Math.min(ASCII_CHARS.length - 1, Math.max(0, Math.floor((value / 255) * ASCII_CHARS.length)));
        return ASCII_CHARS[index];
    }

     /** Genera el frame ASCII SIMÉTRICO basado en los datos de audio */
     function generateAsciiFrame() {
         if (!analyser || !dataArray) return '';

         analyser.getByteFrequencyData(dataArray); // Obtener datos de frecuencia actuales

         // Crear grid vacío
         const grid = Array(OUTPUT_HEIGHT).fill(null).map(() => Array(OUTPUT_WIDTH).fill(' '));

         // Solo necesitamos iterar hasta la mitad del ancho
         const halfWidth = Math.ceil(OUTPUT_WIDTH / 2);
         // Usamos solo la mitad inferior del espectro de frecuencia para la mitad izquierda
         // (Las frecuencias más bajas suelen tener más energía visible)
         const relevantFrequencyBins = Math.floor(dataArray.length / 2); // Considerar solo graves/medios para la forma
         const step = Math.floor(relevantFrequencyBins / halfWidth);

         for (let x = 0; x < halfWidth; x++) {
             let sum = 0;
             // Promediar los bins de frecuencia para esta columna IZQUIERDA
             // Usamos un rango más pequeño de frecuencias para más detalle en los graves/medios
             for (let i = 0; i < step; i++) {
                 const index = x * step + i;
                 // Asegurarse de no salirse del array (aunque relevantFrequencyBins ya limita)
                 if (index < dataArray.length) {
                     sum += dataArray[index];
                 }
             }
             const averageValue = step > 0 ? sum / step : dataArray[x * step] || 0;

             // Calcular la altura de la "barra" para esta columna
             const barHeight = Math.floor((averageValue / 255) * OUTPUT_HEIGHT);

             // Calcular el índice X correspondiente en el lado derecho (reflejado)
             const rightX = OUTPUT_WIDTH - 1 - x;

             // Dibujar la barra en la grid (de abajo hacia arriba) en AMBOS LADOS
             for (let y = 0; y < barHeight; y++) {
                 const gridY = OUTPUT_HEIGHT - 1 - y;
                 if (gridY >= 0) { // Asegurarse de que y no sea negativo
                     const charValue = Math.floor((y / (OUTPUT_HEIGHT -1)) * 255); // Gradiente de carácter
                     const char = mapValueToAscii(charValue + 50);

                     // Dibujar en el lado izquierdo
                     grid[gridY][x] = char;

                     // Dibujar en el lado derecho (solo si no es la misma columna central en caso de ancho impar)
                     if (x !== rightX) {
                        grid[gridY][rightX] = char;
                     }
                 }
             }
         }

         // Convertir la grid a un string
         const asciiFrame = grid.map(row => row.join('')).join('\n');
         return asciiFrame;
     }


    function visualize() { /* ... (Sin cambios) ... */
        if (!isRunning) return;
        const frame = generateAsciiFrame();
        asciiOutput.textContent = frame;
        animationFrameId = requestAnimationFrame(visualize);
    }

    function startVisualization() { /* ... (Sin cambios) ... */
        if (!isInitialized || isRunning) return;
         if (audioContext && audioContext.state === 'suspended') { audioContext.resume(); }
        isRunning = true;
        statusElement.textContent = 'Visualizando...';
        toggleButton.innerHTML = '<i class="bi bi-stop-fill"></i> Detener Visualizador';
        visualize();
        console.log('Visualización iniciada.');
    }

    function stopVisualization() { /* ... (Sin cambios) ... */
        if (!isRunning) return;
        isRunning = false;
        if (animationFrameId) { cancelAnimationFrame(animationFrameId); animationFrameId = null; }
        statusElement.textContent = 'Detenido. Listo para iniciar.';
        toggleButton.innerHTML = '<i class="bi bi-play-fill"></i> Iniciar Visualizador';
        console.log('Visualización detenida.');
    }

    // --- Event Listener (Sin cambios) ---
    toggleButton.addEventListener('click', async () => {
        if (!isInitialized) { toggleButton.disabled = true; await initAudio(); if(isInitialized) { startVisualization(); } } else { if (isRunning) { stopVisualization(); } else { startVisualization(); } }
    });

    // Mensaje inicial (Sin cambios)
    statusElement.textContent = 'Haz clic en Iniciar para comenzar.';
    toggleButton.disabled = false;

}); // Fin de DOMContentLoaded
