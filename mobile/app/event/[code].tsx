import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, FlatList, Image, Pressable, RefreshControl, Text, View } from 'react-native';
import { ApiError, completeUpload, fetchGallery, getPublicEvent, requestUploadUrl, type MediaItem } from '@/src/lib/api';
import { pickMedia, readPickedMediaBytes } from '@/src/lib/media';
import { getEventSession } from '@/src/lib/session';
import { sha256HexFromBytes } from '@/src/lib/sha256';

export default function EventScreen() {
  const { code } = useLocalSearchParams<{ code?: string }>();
  const eventCode = Array.isArray(code) ? code[0] : code ?? '';
  const [items, setItems] = useState<MediaItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const session = useMemo(() => getEventSession(), []);

  useEffect(() => {
    let mounted = true;
    async function load() {
      if (!eventCode) {
        if (mounted) {
          setLoading(false);
          setError('Missing event code.');
        }
        return;
      }
      try {
        await getPublicEvent(eventCode);
        const page = await fetchGallery(eventCode);
        if (mounted) setItems(page.items);
      } catch (err) {
        if (mounted) setError(err instanceof ApiError ? err.message : 'Could not load event.');
      } finally {
        if (mounted) setLoading(false);
      }
    }
    void load();
    return () => {
      mounted = false;
    };
  }, [eventCode]);

  const reload = useCallback(async () => {
    if (!eventCode) return;
    const page = await fetchGallery(eventCode);
    setItems(page.items);
  }, [eventCode]);

  async function uploadFromLibrary() {
    if (!eventCode) return;
    setError(null);
    setUploading(true);
    try {
      const picked = await pickMedia();
      if (!picked) return;
      const bytes = await readPickedMediaBytes(picked.uri);
      const upload = await requestUploadUrl({
        inviteCode: eventCode,
        filename: picked.fileName,
        contentType: picked.contentType,
        sizeBytes: picked.sizeBytes,
        uploaderDisplayName: session?.displayName,
        membershipId: session?.membershipId,
      });
      const response = await fetch(upload.uploadUrl, {
        method: upload.httpMethod,
        headers: { 'Content-Type': upload.requiredContentType },
        body: bytes,
      });
      if (!response.ok) {
        throw new ApiError(`Direct upload failed (${response.status})`, response.status);
      }
      await completeUpload(upload.mediaId, {
        sha256: await sha256HexFromBytes(bytes),
        width: picked.width,
        height: picked.height,
      });
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Upload failed.');
    } finally {
      setUploading(false);
    }
  }

  const onRefresh = useCallback(async () => {
    if (!eventCode) return;
    setRefreshing(true);
    setError(null);
    try {
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not refresh gallery.');
    } finally {
      setRefreshing(false);
    }
  }, [eventCode, reload]);

  return (
    <View style={{ flex: 1, backgroundColor: '#fff' }}>
      <View style={{ padding: 24, paddingTop: 64, backgroundColor: '#0f172a' }}>
        <Text style={{ color: '#fff', fontSize: 28, fontWeight: '800' }}>Event gallery</Text>
        <Text style={{ color: '#cbd5e1', marginTop: 6 }}>{eventCode ? `Shared photos and videos for ${eventCode}.` : 'Shared photos and videos from the event.'}</Text>
      </View>
      <View style={{ padding: 16, gap: 12 }}>
        <Pressable
          onPress={uploadFromLibrary}
          disabled={uploading}
          style={{ backgroundColor: uploading ? '#94a3b8' : '#4f46e5', padding: 14, borderRadius: 14, alignItems: 'center' }}
        >
          {uploading ? <ActivityIndicator color="#fff" /> : <Text style={{ color: '#fff', fontWeight: '700' }}>Upload a photo or video</Text>}
        </Pressable>
        <Pressable onPress={onRefresh} style={{ borderColor: '#cbd5e1', borderWidth: 1, padding: 14, borderRadius: 14, alignItems: 'center' }}>
          <Text style={{ color: '#0f172a', fontWeight: '700' }}>Refresh gallery</Text>
        </Pressable>
        {error ? <Text style={{ color: '#b91c1c', marginTop: 12 }}>{error}</Text> : null}
        <Text style={{ color: '#64748b', fontSize: 13 }}>
          {session?.displayName ? `Uploading as ${session.displayName}` : 'Guest upload mode'}
        </Text>
      </View>
      {loading ? <ActivityIndicator style={{ marginTop: 8 }} /> : null}
      <FlatList
        data={items}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{ padding: 16, gap: 12 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        ListEmptyComponent={
          !loading ? (
            <View style={{ paddingTop: 48, alignItems: 'center' }}>
              <Text style={{ color: '#64748b', textAlign: 'center' }}>
                No media yet. Be the first to add a photo or video.
              </Text>
            </View>
          ) : null
        }
        renderItem={({ item }) => (
          <View style={{ borderRadius: 16, overflow: 'hidden', borderWidth: 1, borderColor: '#e2e8f0' }}>
            {item.thumbnailUrl ? (
              <Image source={{ uri: item.thumbnailUrl }} style={{ width: '100%', height: 220, backgroundColor: '#e2e8f0' }} />
            ) : (
              <View style={{ width: '100%', height: 220, backgroundColor: '#e2e8f0', alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ color: '#64748b' }}>{item.mediaType}</Text>
              </View>
            )}
            <View style={{ padding: 12 }}>
              <Text style={{ fontWeight: '700' }}>{item.uploaderDisplayName ?? 'Guest'}</Text>
              <Text style={{ color: '#64748b' }}>{new Date(item.createdAt).toLocaleString()}</Text>
            </View>
          </View>
        )}
      />
    </View>
  );
}
