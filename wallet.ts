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

/**
 * Refund feature:
 * Calls the Supabase RPC refund_wallet or refund_by_email.
 * It subtracts the refund amount from the user's dashboard balance,
 * sets the remainder as the new balance, and refreshes the balance display in real-time.
 */
export async function refundWallet(
  amount: number,
  targetUserId?: string,
  reason: string = "Refund deduction",
  setWalletBalance?: (balance: number) => void,
  showToast?: (message: string, isError: boolean) => void
): Promise<{ success: boolean; newBalance?: number; error?: string }> {
  if (isNaN(amount) || amount <= 0) {
    const msg = "Please enter a valid refund amount.";
    if (showToast) showToast(msg, true);
    return { success: false, error: msg };
  }

  try {
    const rpcParams: Record<string, any> = { amount, refund_reason: reason };
    if (targetUserId) {
      rpcParams.target_user_id = targetUserId;
    }

    const { data, error } = await supabase.rpc('refund_wallet', rpcParams);

    if (error) {
      console.error('Error processing refund:', error);
      const errorMsg = error.message || "Failed to process refund.";
      if (showToast) showToast(errorMsg, true);
      return { success: false, error: errorMsg };
    }

    const newBal = typeof data === 'number' ? data : Number(data);
    if (setWalletBalance && !isNaN(newBal)) {
      setWalletBalance(newBal);
    } else if (setWalletBalance) {
      await fetchWalletBalance(setWalletBalance);
    }

    if (showToast) {
      showToast(`₦${amount.toLocaleString()} refunded. Remainder: ₦${newBal.toLocaleString()}`, false);
    }

    return { success: true, newBalance: newBal };
  } catch (err: any) {
    console.error('Error during refund:', err);
    const errorMsg = err?.message || "Failed to refund wallet.";
    if (showToast) showToast(errorMsg, true);
    return { success: false, error: errorMsg };
  }
}

export async function refundByEmail(
  amount: number,
  email: string,
  reason: string = "Refund deduction",
  setWalletBalance?: (balance: number) => void,
  showToast?: (message: string, isError: boolean) => void
): Promise<{ success: boolean; newBalance?: number; error?: string }> {
  if (isNaN(amount) || amount <= 0) {
    const msg = "Please enter a valid refund amount.";
    if (showToast) showToast(msg, true);
    return { success: false, error: msg };
  }

  try {
    const { data, error } = await supabase.rpc('refund_by_email', {
      amount,
      user_email: email,
      refund_reason: reason
    });

    if (error) {
      console.error('Error processing refund by email:', error);
      const errorMsg = error.message || "Failed to process refund.";
      if (showToast) showToast(errorMsg, true);
      return { success: false, error: errorMsg };
    }

    const newBal = typeof data === 'number' ? data : Number(data);
    if (setWalletBalance && !isNaN(newBal)) {
      setWalletBalance(newBal);
    }

    if (showToast) {
      showToast(`₦${amount.toLocaleString()} refunded. Remainder: ₦${newBal.toLocaleString()}`, false);
    }

    return { success: true, newBalance: newBal };
  } catch (err: any) {
    console.error('Error during refund by email:', err);
    const errorMsg = err?.message || "Failed to refund wallet.";
    if (showToast) showToast(errorMsg, true);
    return { success: false, error: errorMsg };
  }
}

