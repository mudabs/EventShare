'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

type Mode = 'photo' | 'video';
type Facing = 'user' | 'environment';

export function CameraCapture({
  onCapture,
  onClose
}: {
  onCapture: (file: File) => void;
  onClose: () => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);

  const [mode, setMode] = useState<Mode>('photo');
  const [facing, setFacing] = useState<Facing>('environment');
  const [recording, setRecording] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  }, []);

  const start = useCallback(async () => {
    stop();
    setError(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('Camera capture needs a secure context (https or localhost) and a supported browser.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: facing },
        audio: mode === 'video'
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play().catch(() => undefined);
      }
    } catch {
      setError('Could not access the camera or microphone. Please allow permission and try again.');
    }
  }, [facing, mode, stop]);

  useEffect(() => {
    start();
    return () => stop();
  }, [start, stop]);

  function takePhoto() {
    const video = videoRef.current;
    if (!video) return;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(
      (blob) => {
        if (blob) {
          onCapture(new File([blob], `capture-${Date.now()}.jpg`, { type: 'image/jpeg' }));
          onClose();
        }
      },
      'image/jpeg',
      0.92
    );
  }

  function startRecording() {
    const stream = streamRef.current;
    if (!stream) return;
    chunksRef.current = [];
    const mime = typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported('video/mp4')
      ? 'video/mp4'
      : 'video/webm';
    const recorder = new MediaRecorder(stream, { mimeType: mime });
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunksRef.current.push(e.data);
    };
    recorder.onstop = () => {
      const blob = new Blob(chunksRef.current, { type: mime });
      const ext = mime.includes('mp4') ? 'mp4' : 'webm';
      onCapture(new File([blob], `capture-${Date.now()}.${ext}`, { type: mime }));
      onClose();
    };
    recorder.start();
    recorderRef.current = recorder;
    setRecording(true);
  }

  function stopRecording() {
    recorderRef.current?.stop();
    setRecording(false);
  }

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black/90 p-3">
      <div className="flex items-center justify-between text-white">
        <div className="flex gap-2">
          <button onClick={() => setMode('photo')} disabled={recording} className={`rounded-full px-3 py-1 text-sm ${mode === 'photo' ? 'bg-white text-black' : 'bg-white/20'}`}>Photo</button>
          <button onClick={() => setMode('video')} disabled={recording} className={`rounded-full px-3 py-1 text-sm ${mode === 'video' ? 'bg-white text-black' : 'bg-white/20'}`}>Video</button>
        </div>
        <button onClick={() => { stop(); onClose(); }} className="rounded-full bg-white/20 px-3 py-1 text-sm">Close</button>
      </div>

      <div className="flex flex-1 items-center justify-center overflow-hidden">
        {error ? (
          <p className="max-w-sm text-center text-sm text-white">{error}</p>
        ) : (
          <video ref={videoRef} playsInline muted className="max-h-full max-w-full rounded-lg" />
        )}
      </div>

      <div className="flex items-center justify-center gap-6 py-4">
        <button onClick={() => setFacing((f) => (f === 'user' ? 'environment' : 'user'))} disabled={recording} className="rounded-full bg-white/20 px-4 py-2 text-sm text-white">Flip</button>
        {mode === 'photo' ? (
          <button onClick={takePhoto} disabled={!!error} className="h-16 w-16 rounded-full border-4 border-white bg-white" aria-label="Take photo" />
        ) : recording ? (
          <button onClick={stopRecording} className="h-16 w-16 rounded-full border-4 border-white bg-red-600" aria-label="Stop recording" />
        ) : (
          <button onClick={startRecording} disabled={!!error} className="h-16 w-16 rounded-full border-4 border-white bg-red-500" aria-label="Start recording" />
        )}
        <div className="w-16" />
      </div>
    </div>
  );
}
