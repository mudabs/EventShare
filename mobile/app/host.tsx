import { useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';
import { ApiError, createEvent } from '@/src/lib/api';

export default function HostScreen() {
  const [token, setToken] = useState('');
  const [name, setName] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onCreate() {
    if (!token.trim() || !name.trim()) {
      setMessage('Add a token and event name first.');
      return;
    }
    setLoading(true);
    setMessage(null);
    try {
      const event = await createEvent(token.trim(), {
        name: name.trim(),
        eventType: 'OTHER',
        allowGuestDownloads: true,
        autoApprove: true,
      });
      setMessage(`Created ${event.name}. Invite code: ${event.inviteCode}`);
    } catch (err) {
      setMessage(err instanceof ApiError ? err.message : 'Could not create event.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <View style={{ flex: 1, padding: 24, backgroundColor: '#fff', justifyContent: 'center' }}>
      <Text style={{ fontSize: 30, fontWeight: '800', marginBottom: 8 }}>Host tools</Text>
      <Text style={{ color: '#475569', marginBottom: 20 }}>
        Guest flows are ready. Host creation needs a valid Clerk token from your existing web session.
      </Text>
      <TextInput placeholder="Clerk token" autoCapitalize="none" autoCorrect={false} value={token} onChangeText={setToken} style={inputStyle} />
      <View style={{ height: 12 }} />
      <TextInput placeholder="Event name" value={name} onChangeText={setName} style={inputStyle} />
      <View style={{ height: 16 }} />
      <Pressable onPress={onCreate} disabled={loading} style={{ backgroundColor: loading ? '#94a3b8' : '#0f172a', padding: 16, borderRadius: 14, alignItems: 'center' }}>
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={{ color: '#fff', fontWeight: '700' }}>Create event</Text>}
      </Pressable>
      {message ? <Text style={{ marginTop: 14, color: '#0f172a' }}>{message}</Text> : null}
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
