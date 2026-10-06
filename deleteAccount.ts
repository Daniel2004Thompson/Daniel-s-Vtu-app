import { supabase } from "./supabaseClient"

export async function deleteAccount(): Promise<{ success: boolean; error?: string }> {
  const { error } = await supabase.rpc('delete_user')

  if (error) {
    console.error(error)
    return { success: false, error: error.message }
  }

  await supabase.auth.signOut()
  return { success: true }
}

// Example usage:
// const handleDelete = async () => {
//   const { success, error } = await deleteAccount()
//   if (success) {
//     // navigate to sign-in, show success toast
//   } else {
//     console.error(error)
//   }
// }
