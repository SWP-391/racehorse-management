/** Letters without Vietnamese diacritics and in lower case, so a search matches "Bạch Mã" typed as "bach ma". */
export const fold = (text: string): string =>
  text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/gi, 'd')
    .toLowerCase();
