import { Link } from 'expo-router';
import { Pressable, Text, View } from 'react-native';

export default function Home() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0f172a', padding: 24, justifyContent: 'center' }}>
      <Text style={{ color: '#fff', fontSize: 36, fontWeight: '800', marginBottom: 12 }}>
        Every photo from your event, in one place.
      </Text>
      <Text style={{ color: '#cbd5e1', fontSize: 17, lineHeight: 24, marginBottom: 28 }}>
        Join an event by code, upload from your phone, and browse the shared gallery.
      </Text>

      <Link href="/join" asChild>
        <Pressable style={{ backgroundColor: '#4f46e5', padding: 16, borderRadius: 14 }}>
          <Text style={{ color: '#fff', textAlign: 'center', fontWeight: '700' }}>Join an event</Text>
        </Pressable>
      </Link>

      <View style={{ height: 14 }} />
      <Link href="/host" asChild>
        <Pressable style={{ borderColor: '#334155', borderWidth: 1, padding: 16, borderRadius: 14 }}>
          <Text style={{ color: '#e2e8f0', textAlign: 'center', fontWeight: '700' }}>Host tools</Text>
        </Pressable>
      </Link>
    </View>
  );
}
