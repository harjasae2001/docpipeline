import { readFile } from 'node:fs/promises';

const required = ['SUPABASE_URL', 'SUPABASE_SECRET_KEY', 'USER_EXPORT_PATH'];
for (const name of required) {
  if (!process.env[name]) throw new Error(`${name} is required`);
}

const users = JSON.parse(await readFile(process.env.USER_EXPORT_PATH, 'utf8'));
if (!Array.isArray(users)) throw new Error('User export must be a JSON array');

for (const user of users) {
  if (!user.id || !user.email || !user.password_hash) {
    throw new Error('Each imported user requires id, email, and password_hash');
  }
  const role = user.role ?? 'USER';
  if (!['USER', 'ADMIN'].includes(role)) {
    throw new Error(`Unsupported role for ${user.email}`);
  }
  const response = await fetch(`${process.env.SUPABASE_URL}/auth/v1/admin/users`, {
    method: 'POST',
    headers: {
      apikey: process.env.SUPABASE_SECRET_KEY,
      Authorization: `Bearer ${process.env.SUPABASE_SECRET_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      id: user.id,
      email: user.email,
      password_hash: user.password_hash,
      email_confirm: user.email_confirmed ?? true,
      user_metadata: { full_name: user.full_name },
      app_metadata: { app_role: role },
    }),
  });
  if (!response.ok) {
    throw new Error(`Failed to import ${user.email}: ${response.status} ${await response.text()}`);
  }
  console.log(`Imported ${user.email}`);
}
