import { createClient } from "@supabase/supabase-js";

const supabaseUrl = process.env.SUPABASE_URL || "https://yjymxdzdhvbdjramlipg.supabase.co";
const supabaseAnonKey = process.env.SUPABASE_ANON_KEY || "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlqeW14ZHpkaHZiZGpyYW1saXBnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2MTk0MjYsImV4cCI6MjEwNDE5NTQyNn0.nQQcsHTScnXT2XQxSW51GlDTEmCkvR8FMKGfG1g0VPU";

export const supabase = createClient(supabaseUrl, supabaseAnonKey);
