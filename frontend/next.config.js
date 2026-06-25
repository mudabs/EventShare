/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Produces a minimal self-contained server bundle for the Docker image.
  output: 'standalone'
};

module.exports = nextConfig;
