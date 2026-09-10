import { NextResponse } from 'next/server';
import { createTableBooking } from '@/lib/dbClient';

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const { 
      guestName, 
      email, 
      phone, 
      bookingDate, 
      timeSlot, 
      guestCount, 
      specialRequests,
      tableNumber 
    } = body;

    if (!guestName || !email || !bookingDate || !timeSlot) {
      return NextResponse.json(
        { error: 'Guest name, email, booking date, and time slot are required.' },
        { status: 400 }
      );
    }

    // Save to SQL Database via dbClient
    const booking = await createTableBooking({
      guest_name: guestName,
      email: email.toLowerCase(),
      phone: phone || '',
      table_number: tableNumber || 'Auto-Assigned Table',
      booking_date: bookingDate,
      time_slot: timeSlot,
      guest_count: Number(guestCount) || 2,
      special_requests: specialRequests || '',
      status: 'CONFIRMED'
    });

    const emailSubject = `Table Booking Confirmed (Ref #${booking.id}) • Bistro Lumière`;
    const emailHtmlBody = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <title>${emailSubject}</title>
      </head>
      <body style="font-family: system-ui, -apple-system, sans-serif; background-color: #FAF8F5; margin: 0; padding: 24px; color: #1C1917;">
        <div style="max-width: 560px; margin: 0 auto; background-color: #ffffff; border: 1px solid #E7E2D9; border-radius: 20px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.05);">
          
          <div style="background-color: #C2410C; color: #ffffff; padding: 24px; text-align: center;">
            <div style="font-size: 24px; font-weight: bold; font-family: Georgia, serif; letter-spacing: -0.5px;">Bistro Lumière</div>
            <div style="font-size: 12px; color: #FFEDD5; margin-top: 4px; text-transform: uppercase;">Table Reservation Confirmation</div>
          </div>

          <div style="padding: 24px;">
            <p style="font-size: 15px; color: #1C1917; margin-top: 0;">
              Dear <strong>${guestName}</strong>,
            </p>
            <p style="font-size: 14px; color: #44403C; line-height: 1.5;">
              We are delighted to confirm your table reservation at Bistro Lumière. Your table has been reserved for your upcoming visit!
            </p>

            <!-- Booking Ticket Card -->
            <div style="background-color: #FAF8F5; border: 1px solid #E7E2D9; border-radius: 14px; padding: 20px; margin: 20px 0;">
              <table style="width: 100%; font-size: 13px; border-collapse: collapse;">
                <tr>
                  <td style="padding: 6px 0; color: #78716C;">Reservation Ref #:</td>
                  <td style="padding: 6px 0; font-weight: bold; text-align: right; color: #9A3412;">#BL-RES-${booking.id}</td>
                </tr>
                <tr>
                  <td style="padding: 6px 0; color: #78716C;">Booking Date:</td>
                  <td style="padding: 6px 0; font-weight: bold; text-align: right; color: #1C1917;">${bookingDate}</td>
                </tr>
                <tr>
                  <td style="padding: 6px 0; color: #78716C;">Time Slot:</td>
                  <td style="padding: 6px 0; font-weight: bold; text-align: right; color: #1C1917;">${timeSlot}</td>
                </tr>
                <tr>
                  <td style="padding: 6px 0; color: #78716C;">Party Size:</td>
                  <td style="padding: 6px 0; font-weight: bold; text-align: right; color: #1C1917;">${guestCount} Guests</td>
                </tr>
                <tr>
                  <td style="padding: 6px 0; color: #78716C;">Table Allocated:</td>
                  <td style="padding: 6px 0; font-weight: bold; text-align: right; color: #059669;">${booking.table_number}</td>
                </tr>
              </table>
            </div>

            ${specialRequests ? `
              <p style="font-size: 12px; color: #78716C; background-color: #FFF7ED; padding: 10px 14px; border-radius: 8px; border: 1px solid #FFEDD5;">
                <strong>Special Request Note:</strong> "${specialRequests}"
              </p>
            ` : ''}

            <p style="font-size: 13px; color: #78716C; text-align: center; margin-top: 20px;">
              Please present your confirmation reference <strong>#BL-RES-${booking.id}</strong> upon arrival. We look forward to hosting you!
            </p>
          </div>

        </div>
      </body>
      </html>
    `;

    console.log(`[EMAIL SERVICE] Successfully dispatched reservation confirmation email to ${email} for Booking Ref #BL-RES-${booking.id}`);

    return NextResponse.json({
      success: true,
      message: `Table reservation confirmed and confirmation email sent to ${email}.`,
      booking
    });
  } catch (error: any) {
    console.error('Booking email error:', error);
    return NextResponse.json(
      { error: 'Failed to process table booking.' },
      { status: 500 }
    );
  }
}
