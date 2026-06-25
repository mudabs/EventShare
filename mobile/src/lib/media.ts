import * as ImagePicker from 'expo-image-picker';
import * as FileSystem from 'expo-file-system';

export type PickedAsset = {
  uri: string;
  fileName: string;
  contentType: string;
  width?: number;
  height?: number;
  sizeBytes: number;
  mediaType: 'PHOTO' | 'VIDEO';
};

export async function pickMedia(): Promise<PickedAsset | null> {
  const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted) {
    throw new Error('Media library permission is required to upload files.');
  }

  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ImagePicker.MediaTypeOptions.All,
    quality: 0.9,
    allowsMultipleSelection: false,
    presentationStyle: ImagePicker.UIImagePickerPresentationStyle.FullScreen,
  });
  if (result.canceled) return null;

  const asset = result.assets[0];
  const mediaType = asset.type === 'video' ? 'VIDEO' : 'PHOTO';
  return {
    uri: asset.uri,
    fileName: asset.fileName ?? `upload-${Date.now()}${mediaType === 'VIDEO' ? '.mp4' : '.jpg'}`,
    contentType: asset.mimeType ?? (mediaType === 'VIDEO' ? 'video/mp4' : 'image/jpeg'),
    width: asset.width,
    height: asset.height,
    sizeBytes: asset.fileSize ?? 0,
    mediaType,
  };
}

export async function readPickedMediaBytes(uri: string): Promise<ArrayBuffer> {
  const base64 = await FileSystem.readAsStringAsync(uri, { encoding: FileSystem.EncodingType.Base64 });
  const binary = globalThis.atob ? globalThis.atob(base64) : Buffer.from(base64, 'base64').toString('binary');
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}
