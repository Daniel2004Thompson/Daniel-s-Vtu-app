import { supabase } from "./supabaseClient";

/**
 * Balance-fetching logic using Supabase Auth & 'users' table
 */
export async function fetchWalletBalance(setWalletBalance: (balance: number) => void) {
  const { data: { user } } = await supabase.auth.getUser();

  if (user) {
    const { data, error } = await supabase
      .from('users')
      .select('wallet_balance')
      .eq('id', user.id)
      .single();

    if (error) {
      console.error('Error fetching wallet balance:', error);
    } else {
      setWalletBalance(data.wallet_balance);
    }
  }
}

/**
 * Withdraw / debit feature:
 * Calls the Supabase RPC debit_wallet with an amount,
 * shows an error toast if it fails (e.g. insufficient balance),
 * and refreshes the displayed wallet_balance after it succeeds.
 */
export async function debitWallet(
  amount: number,
  setWalletBalance: (balance: number) => void,
  showToast?: (message: string, isError: boolean) => void
): Promise<{ success: boolean; error?: string }> {
  if (isNaN(amount) || amount <= 0) {
    const msg = "Please enter a valid withdrawal amount.";
    if (showToast) showToast(msg, true);
    return { success: false, error: msg };
  }

  try {
    const { data, error } = await supabase.rpc('debit_wallet', { amount });

    if (error) {
      console.error('Error debiting wallet:', error);
      const errorMsg = error.message || "Failed to debit wallet. Insufficient balance.";
      if (showToast) showToast(errorMsg, true);
      return { success: false, error: errorMsg };
    }

    // Refresh the displayed wallet_balance after it succeeds
    await fetchWalletBalance(setWalletBalance);

    if (showToast) {
      showToast(`₦${amount.toLocaleString()} withdrawn successfully`, false);
    }

    return { success: true };
  } catch (err: any) {
    console.error('Error during withdrawal:', err);
    const errorMsg = err?.message || "Failed to debit wallet. Insufficient balance.";
    if (showToast) showToast(errorMsg, true);
    return { success: false, error: errorMsg };
  }
}
