// Vite's `?raw` import (the file's text), used by the test that checks every client route has an access entry.
declare module '*?raw' {
  const content: string;
  export default content;
}
