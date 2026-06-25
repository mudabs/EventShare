import { useMemo, useState } from 'react';
import { Link, router } from 'expo-router';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';
import { ApiError, getPublicEvent, joinEvent } from '@/src/lib/api';
import { setEventSession } from '@/src/lib/session';

export default function JoinScreen() {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const normalizedCode = useMemo(() => code.trim().toUpperCase(), [code]);

  async function onJoin() {
    if (!normalizedCode || !name.trim()) {
      setError('Please enter both an invite code and a display name.');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await getPublicEvent(normalizedCode);
      const session = await joinEvent(normalizedCode, name.trim());
      setEventSession({ inviteCode: normalizedCode, membershipId: session.membershipId, displayName: session.displayName });
      router.push(`/event/${normalizedCode}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not join this event.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <View style={{ flex: 1, padding: 24, backgroundColor: '#f8fafc', justifyContent: 'center' }}>
      <Text style={{ fontSize: 30, fontWeight: '800', marginBottom: 8 }}>Join by code</Text>
      <Text style={{ color: '#475569', marginBottom: 20 }}>
        Enter the invite code from your host and pick a display name.
      </Text>
      <TextInput
        placeholder="Invite code"
        autoCapitalize="characters"
        autoCorrect={false}
        maxLength={24}
        value={code}
        onChangeText={setCode}
        style={inputStyle}
      />
      <View style={{ height: 12 }} />
      <TextInput placeholder="Your name" value={name} onChangeText={setName} style={inputStyle} />
      <View style={{ height: 16 }} />
      <Pressable onPress={onJoin} style={{ backgroundColor: '#0f172a', padding: 16, borderRadius: 14, alignItems: 'center' }}>
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={{ color: '#fff', fontWeight: '700' }}>Join event</Text>}
      </Pressable>
      {error ? <Text style={{ color: '#b91c1c', marginTop: 14 }}>{error}</Text> : null}
      <Link href="/host" style={{ marginTop: 16, color: '#4f46e5', fontWeight: '700' }}>
        I am the host
      </Link>
    </View>
  );
}

const inputStyle = {
  borderWidth: 1,
  borderColor: '#cbd5e1',
  borderRadius: 14,
  paddingHorizontal: 14,
  paddingVertical: 14,
  backgroundColor: '#fff',
};
