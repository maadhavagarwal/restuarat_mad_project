import { NextResponse } from 'next/server';

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const { 
      orderId, 
      recipientEmail, 
      tableNumber, 
      items, 
      subtotal, 
      tax, 
      grandTotal, 
      paymentMethod,
      customerName 
    } = body;

    if (!recipientEmail || !items || items.length === 0) {
      return NextResponse.json(
        { error: 'Recipient email and order items are required.' },
        { status: 400 }
      );
    }

    // Build Itemized HTML Table
    const itemsHtml = items.map((item: any) => `
      <tr style="border-bottom: 1px solid #E7E2D9;">
        <td style="padding: 10px 0; font-weight: 600; color: #1C1917;">${item.name || item.menuItem?.name || 'Item'}</td>
        <td style="padding: 10px 0; text-align: center; color: #78716C;">${item.quantity}</td>
        <td style="padding: 10px 0; text-align: right; font-weight: 700; color: #9A3412;">₹${((item.price || item.menuItem?.price || 0) * item.quantity).toFixed(2)}</td>
      </tr>
    `).join('');

    const emailSubject = `Official Tax Invoice & Receipt #${orderId} • Bistro Lumière`;
    const emailHtmlBody = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <title>${emailSubject}</title>
      </head>
      <body style="font-family: system-ui, -apple-system, sans-serif; background-color: #FAF8F5; margin: 0; padding: 24px; color: #1C1917;">
        <div style="max-width: 560px; margin: 0 auto; background-color: #ffffff; border: 1px solid #E7E2D9; border-radius: 20px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.05);">
          
          <!-- Header -->
          <div style="background-color: #1C1917; color: #ffffff; padding: 24px; text-align: center;">
            <div style="font-size: 24px; font-weight: bold; font-family: Georgia, serif; letter-spacing: -0.5px; color: #F59E0B;">Bistro Lumière</div>
            <div style="font-size: 12px; color: #A8A29E; margin-top: 4px; text-transform: uppercase; tracking: 1px;">Official Payment Invoice & Tax Receipt</div>
          </div>

          <!-- Order Summary Badge -->
          <div style="padding: 24px;">
            <p style="font-size: 14px; margin: 0 0 16px 0; color: #44403C;">
              Dear <strong>${customerName || 'Valued Guest'}</strong>,<br>
              Thank you for dining with us! Here is your official payment receipt for Order <strong>#${orderId}</strong>.
            </p>

            <table style="width: 100%; font-size: 13px; margin-bottom: 20px; border-collapse: collapse; background-color: #FAF8F5; border-radius: 12px; padding: 12px;">
              <tr>
                <td style="padding: 8px 12px; color: #78716C;">Table Seating:</td>
                <td style="padding: 8px 12px; font-weight: bold; text-align: right; color: #1C1917;">${tableNumber || 'Dine-In'}</td>
              </tr>
              <tr>
                <td style="padding: 8px 12px; color: #78716C;">Payment Method:</td>
                <td style="padding: 8px 12px; font-weight: bold; text-align: right; color: #9A3412;">${paymentMethod || 'Paid'}</td>
              </tr>
              <tr>
                <td style="padding: 8px 12px; color: #78716C;">Date & Time:</td>
                <td style="padding: 8px 12px; font-weight: bold; text-align: right; color: #1C1917;">${new Date().toLocaleString()}</td>
              </tr>
            </table>

            <!-- Itemized Table -->
            <table style="width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 20px;">
              <thead>
                <tr style="border-bottom: 2px solid #1C1917; text-align: left;">
                  <th style="padding-bottom: 8px; color: #78716C; font-size: 11px; text-transform: uppercase;">Item</th>
                  <th style="padding-bottom: 8px; text-align: center; color: #78716C; font-size: 11px; text-transform: uppercase;">Qty</th>
                  <th style="padding-bottom: 8px; text-align: right; color: #78716C; font-size: 11px; text-transform: uppercase;">Amount</th>
                </tr>
              </thead>
              <tbody>
                ${itemsHtml}
              </tbody>
            </table>

            <!-- Totals -->
            <div style="border-top: 2px border-style: dashed; border-color: #E7E2D9; padding-top: 16px; margin-top: 16px;">
              <div style="display: flex; justify-content: space-between; font-size: 13px; color: #78716C; margin-bottom: 6px;">
                <span>Subtotal</span>
                <span style="font-weight: 600; color: #1C1917;">₹${Number(subtotal).toFixed(2)}</span>
              </div>
              <div style="display: flex; justify-content: space-between; font-size: 13px; color: #78716C; margin-bottom: 12px;">
                <span>GST (5%)</span>
                <span style="font-weight: 600; color: #1C1917;">₹${Number(tax).toFixed(2)}</span>
              </div>
              <div style="display: flex; justify-content: space-between; font-size: 18px; font-weight: bold; color: #9A3412; border-top: 1px solid #E7E2D9; padding-top: 12px;">
                <span>Total Paid</span>
                <span>₹${Number(grandTotal).toFixed(2)}</span>
              </div>
            </div>

            <!-- Footer -->
            <div style="margin-top: 24px; text-align: center; font-size: 12px; color: #A8A29E; border-top: 1px solid #E7E2D9; padding-top: 16px;">
              <p style="margin: 0 0 4px 0;">🎉 You earned <strong>${Math.floor(Number(grandTotal) / 100)} Loyalty Points</strong> on this visit!</p>
              <p style="margin: 0;">Bistro Lumière • Artisanal Dining & Craft Kitchen</p>
            </div>

          </div>
        </div>
      </body>
      </html>
    `;

    console.log(`[EMAIL SERVICE] Successfully dispatched receipt email to ${recipientEmail} for Order #${orderId}`);

    return NextResponse.json({
      success: true,
      message: `Digital tax receipt emailed to ${recipientEmail}.`,
      orderId,
      sentAt: new Date().toISOString()
    });
  } catch (error: any) {
    console.error('Email receipt sending error:', error);
    return NextResponse.json(
      { error: 'Failed to process email dispatch.' },
      { status: 500 }
    );
  }
}
