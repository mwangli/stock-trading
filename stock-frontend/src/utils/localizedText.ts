// AI_GENERATE_START -------------------------
import i18n from '../i18n';

/** Returns the requested static UI label for the active application language. */
export const localizedText = (zh: string, en: string) => i18n.language.startsWith('en') ? en : zh;
// AI_GENERATE_END ---------------------------
