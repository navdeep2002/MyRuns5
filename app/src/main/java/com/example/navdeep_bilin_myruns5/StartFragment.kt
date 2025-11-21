package com.example.navdeep_bilin_myruns5

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Spinner
import androidx.fragment.app.Fragment

class StartFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?

        //  lecture reference for standard fragment inflation from the layout
    ): View? = inflater.inflate(R.layout.fragment_start, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spInput = view.findViewById<Spinner>(R.id.spInputType) // lecture spinner binding
        val btnStart = view.findViewById<Button>(R.id.btnStart) // lecture button binding

        btnStart.setOnClickListener {

            /*val inputPos = spInput.selectedItemPosition  // 0=Manual, 1=GPS, 2=Automatic

            if (inputPos == 1 || inputPos == 2) {
                // GPS or Automatic -> open blank Maps page
                startActivity(Intent(requireContext(), MapActivity::class.java))
            } else {
                startActivity(Intent(requireContext(), ManualEntryActivity::class.java))
            }*/

            val spInput  = view.findViewById<Spinner>(R.id.spInputType)
            val spActivity = view.findViewById<Spinner>(R.id.spActivityType)

            val inputPos = spInput.selectedItemPosition      // 0-Manual, 1-GPS, 2-Automatic
            val activityPos = spActivity.selectedItemPosition   // walking / running / biking …

            if (inputPos == 1 || inputPos == 2) {
                // GPS or Automatic → open MapActivity
                val intent = Intent(requireContext(), MapActivity::class.java).apply {
                    putExtra("input_type", inputPos)
                    putExtra("activity_type", activityPos)
                }
                startActivity(intent)
            } else {
                // Manual
                val intent = Intent(requireContext(), ManualEntryActivity::class.java).apply {
                    putExtra("activity_type", activityPos)
                }
                startActivity(intent)
            }
        }
    }
}